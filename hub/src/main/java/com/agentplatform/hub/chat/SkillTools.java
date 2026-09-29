package com.agentplatform.hub.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SkillTools {

	private SkillTools() {
	}

	static List<Map<String, Object>> definitions() {
		List<Map<String, Object>> tools = new ArrayList<>();
		tools.add(function("load_skill", "读取已绑定技能的 SKILL.md 说明书，并列出可按需加载的参考文件与脚本。",
				Map.of("skill_id", stringProp("技能目录 id，例如 customer-reply")),
				List.of("skill_id")));
		tools.add(function("read_skill_resource", "读取技能目录中的参考文件或模板。不要用它读取 scripts/ 下的可执行文件。",
				Map.of(
						"skill_id", stringProp("技能目录 id"),
						"path", stringProp("相对路径，例如 references/input.md")),
				List.of("skill_id", "path")));
		tools.add(function("run_skill_script", "执行技能 scripts/ 目录中的脚本，返回 stdout。",
				Map.of(
						"skill_id", stringProp("技能目录 id"),
						"script", stringProp("脚本文件名或 scripts/ 下的相对路径"),
						"args", Map.of(
								"type", "array",
								"description", "传给脚本的命令行参数",
								"items", Map.of("type", "string"))),
				List.of("skill_id", "script")));
		return tools;
	}

	private static Map<String, Object> function(String name, String description, Map<String, Object> properties, List<String> required) {
		Map<String, Object> parameters = new LinkedHashMap<>();
		parameters.put("type", "object");
		parameters.put("properties", properties);
		parameters.put("required", required);
		Map<String, Object> fn = new LinkedHashMap<>();
		fn.put("name", name);
		fn.put("description", description);
		fn.put("parameters", parameters);
		Map<String, Object> tool = new LinkedHashMap<>();
		tool.put("type", "function");
		tool.put("function", fn);
		return tool;
	}

	private static Map<String, Object> stringProp(String description) {
		return Map.of("type", "string", "description", description);
	}

}
