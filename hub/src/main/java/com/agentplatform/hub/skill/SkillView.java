package com.agentplatform.hub.skill;

public record SkillView(String id, String name, String description, String body, int latestVersion) {

	public SkillView(String id, String name, String description, String body) {
		this(id, name, description, body, 0);
	}

	public SkillView withLatestVersion(int latestVersion) {
		return new SkillView(id, name, description, body, latestVersion);
	}

}
