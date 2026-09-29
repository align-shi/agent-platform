package com.agentplatform.hub.trace;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class AgentCallSchemaMigrator implements ApplicationRunner {

	private final JdbcTemplate jdbc;

	public AgentCallSchemaMigrator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments args) {
		String type = jdbc.queryForObject(
				"""
				select data_type
				from information_schema.columns
				where table_schema = database()
				  and table_name = 'agent_calls'
				  and column_name = 'trace_json'
				""",
				String.class);
		if (type != null && !"longtext".equalsIgnoreCase(type)) {
			jdbc.execute("alter table agent_calls modify column trace_json longtext not null");
		}
	}

}
