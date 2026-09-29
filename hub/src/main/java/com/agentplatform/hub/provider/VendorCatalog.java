package com.agentplatform.hub.provider;

import java.util.List;

public final class VendorCatalog {

	private VendorCatalog() {
	}

	public record Preset(
			String id,
			String name,
			String baseUrl,
			String defaultModel,
			ProviderType type,
			Integer dimensions) {
	}

	public static final List<Preset> ALL = List.of(
			new Preset("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat", ProviderType.CHAT, null),
			new Preset("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", ProviderType.CHAT, null),
			new Preset("openai", "OpenAI", "https://api.openai.com", "gpt-4o-mini", ProviderType.CHAT, null),
			new Preset(
					"qwen",
					"通义千问",
					"https://dashscope.aliyuncs.com/compatible-mode/v1",
					"qwen-plus",
					ProviderType.CHAT,
					null),
			new Preset("moonshot", "Moonshot 月之暗面", "https://api.moonshot.cn/v1", "moonshot-v1-8k", ProviderType.CHAT, null),
			new Preset("custom", "自定义对话", "", "", ProviderType.CHAT, null),
			new Preset(
					"qwen-embed",
					"通义千问 Embedding",
					"https://dashscope.aliyuncs.com/compatible-mode/v1",
					"text-embedding-v4",
					ProviderType.EMBEDDING,
					1024),
			new Preset(
					"zhipu-embed",
					"智谱 Embedding",
					"https://open.bigmodel.cn/api/paas/v4",
					"embedding-3",
					ProviderType.EMBEDDING,
					1024),
			new Preset(
					"openai-embed",
					"OpenAI Embedding",
					"https://api.openai.com/v1",
					"text-embedding-3-small",
					ProviderType.EMBEDDING,
					1536),
			new Preset("custom-embed", "自定义 Embedding", "", "", ProviderType.EMBEDDING, null));

}
