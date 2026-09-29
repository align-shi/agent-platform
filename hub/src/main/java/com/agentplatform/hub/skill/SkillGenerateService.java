package com.agentplatform.hub.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.agentplatform.hub.chat.ChatProxyService;
import com.agentplatform.hub.provider.ProviderEntity;
import com.agentplatform.hub.provider.ProviderService;

@Service
public class SkillGenerateService {

	static final String SYSTEM_PROMPT = """
			你是技能编写器。根据用户描述，生成一个可供智能体按需加载的技能。
			只输出一个 JSON 对象，不要 Markdown 代码围栏，不要解释。

			JSON 字段：
			- name：技能显示名，简短。
			- code：小写字母、数字和中划线，例如 customer-reply。
			- description：一两句，说明什么时候该用这个技能。
			- body：SKILL.md 正文，使用 Markdown，不要包含 YAML front matter。写清可执行步骤。需要读参考文件或运行脚本时，写出对应路径。
			- files：数组，可以是空数组。每项包含 path 和 content。
			  path 必须是相对路径。每一段只能包含字母、数字、点、下划线或中划线，且不能以点开头。
			  参考材料和模板放在 references/ 或 templates/，可以有子目录。
			  脚本只能放在 scripts/ 的第一层，例如 scripts/check.js，不能再建子文件夹。
			  不要输出 SKILL.md、.versions，也不要输出空文件夹。
			  只在确实需要时才添加文件，通常 0 到 3 个。
			""";

	static final String REVISE_PROMPT = """
			你是技能修改器。用户会给出一份已有技能和修改说明。
			在现有内容上修改，不要另写一个新技能。
			只输出一个 JSON 对象，不要 Markdown 代码围栏，不要解释。

			JSON 字段：
			- name：修改后的显示名。说明没要求改名时保持原名。
			- code：必须与现有编码完全相同。
			- description：修改后的描述。说明没要求改描述时保持原描述。
			- body：修改后的完整 SKILL.md 正文，使用 Markdown，不要包含 YAML front matter。输出全文，不要只输出差异。只改说明里要求改的部分。
			- files：只包含这次有改动或新增的文件。每项包含 path 和 content，content 是该文件修改后的完整内容。
			  没有改动的文件不要输出。
			  path 必须是相对路径。每一段只能包含字母、数字、点、下划线或中划线，且不能以点开头。
			  参考材料和模板放在 references/ 或 templates/，可以有子目录。
			  脚本只能放在 scripts/ 的第一层，例如 scripts/check.js，不能再建子文件夹。
			  不要输出 SKILL.md、.versions，也不要输出空文件夹。
			""";

	private static final int CONTEXT_FILES = 8;
	private static final int CONTEXT_CHARS = 8_000;

	private final ProviderService providers;
	private final ChatProxyService chatProxy;
	private final SkillService skills;

	public SkillGenerateService(ProviderService providers, ChatProxyService chatProxy, SkillService skills) {
		this.providers = providers;
		this.chatProxy = chatProxy;
		this.skills = skills;
	}

	public Generated generate(String brief) {
		return generate(brief, null, List.of());
	}

	public Generated generate(String brief, String skillId, List<CurrentFile> openFiles) {
		if (skillId == null || skillId.isBlank()) {
			return complete(SYSTEM_PROMPT, brief);
		}
		return revise(brief, skillId.trim(), openFiles == null ? List.of() : openFiles);
	}

	private Generated revise(String brief, String skillId, List<CurrentFile> openFiles) {
		if (brief == null || brief.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写要修改的说明");
		}
		SkillView skill = skills.require(skillId);
		String body = "";
		List<GeneratedFile> files = new ArrayList<>();
		for (String path : skills.listFiles(skillId)) {
			if (path.endsWith("/")) {
				continue;
			}
			SkillFileView file = skills.readFile(skillId, path);
			String content = overlay(openFiles, file.path(), file.content());
			if ("SKILL.md".equals(file.path())) {
				body = content;
				continue;
			}
			if (files.size() >= CONTEXT_FILES) {
				continue;
			}
			files.add(new GeneratedFile(file.path(), clipText(content, CONTEXT_CHARS)));
		}
		body = overlay(openFiles, "SKILL.md", body);
		Generated generated = complete(REVISE_PROMPT, revisionInput(
				skill.name(),
				skill.id(),
				skill.description(),
				clipText(body, SkillDraftParser.MAX_CHARS),
				files,
				brief));
		String name = generated.name() == null || generated.name().isBlank() ? skill.name() : generated.name();
		String description = generated.description() == null || generated.description().isBlank()
				? (skill.description() == null ? "" : skill.description())
				: generated.description();
		return new Generated(
				name,
				skill.id(),
				description,
				generated.body(),
				generated.files(),
				generated.skipped(),
				generated.providerName(),
				generated.model());
	}

	private Generated complete(String systemPrompt, String brief) {
		if (brief == null || brief.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写要生成的技能说明");
		}
		List<ProviderEntity> ready = providers.usableChatProviders();
		if (ready.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可用的 Chat 模型，请先配置提供商和默认模型");
		}
		ProviderEntity provider = ready.get(ThreadLocalRandom.current().nextInt(ready.size()));
		String raw;
		try {
			raw = chatProxy.completeText(
					provider,
					providers.decryptApiKey(provider),
					provider.getDefaultModel().trim(),
					systemPrompt,
					brief.trim());
		}
		catch (RuntimeException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, clip(ex.getMessage()));
		}
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型没有返回内容");
		}
		SkillDraftParser.Draft draft = SkillDraftParser.parse(raw);
		List<GeneratedFile> files = draft.files().stream()
				.map((file) -> new GeneratedFile(file.path(), file.content()))
				.toList();
		return new Generated(
				draft.name(),
				draft.code(),
				draft.description(),
				draft.body(),
				files,
				draft.skipped(),
				provider.getName(),
				provider.getDefaultModel().trim());
	}

	static String revisionInput(
			String name,
			String code,
			String description,
			String body,
			List<GeneratedFile> files,
			String brief) {
		StringBuilder text = new StringBuilder();
		text.append("现有技能\n");
		text.append("名称：").append(name == null ? "" : name).append('\n');
		text.append("编码：").append(code == null ? "" : code).append('\n');
		text.append("描述：").append(description == null ? "" : description).append("\n\n");
		text.append("SKILL.md：\n").append(body == null ? "" : body).append("\n\n");
		if (files != null) {
			for (GeneratedFile file : files) {
				text.append("文件 ").append(file.path()).append("：\n");
				text.append(file.content() == null ? "" : file.content()).append("\n\n");
			}
		}
		text.append("修改说明：\n").append(brief == null ? "" : brief.trim());
		return text.toString();
	}

	private static String overlay(List<CurrentFile> openFiles, String path, String saved) {
		if (openFiles == null) {
			return saved == null ? "" : saved;
		}
		for (CurrentFile file : openFiles) {
			if (file != null && path.equals(file.path()) && file.content() != null) {
				return file.content();
			}
		}
		return saved == null ? "" : saved;
	}

	private static String clipText(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	private static String clip(String message) {
		if (message == null || message.isBlank()) {
			return "调用模型失败";
		}
		String text = message.trim().replaceAll("\\s+", " ");
		return text.length() <= 300 ? text : text.substring(0, 300);
	}

	public record CurrentFile(String path, String content) {
	}

	public record GeneratedFile(String path, String content) {
	}

	public record Generated(
			String name,
			String code,
			String description,
			String body,
			List<GeneratedFile> files,
			List<String> skipped,
			String providerName,
			String model) {
	}

}
