package com.agentplatform.hub.trace;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/calls")
public class CallController {

	private final CallTraceService traces;

	public CallController(CallTraceService traces) {
		this.traces = traces;
	}

	@GetMapping
	public List<CallDtos.Summary> list(@RequestParam(required = false) String agentId) {
		return traces.list(agentId);
	}

	@GetMapping("/{id}")
	public CallDtos.Detail get(@PathVariable String id) {
		return traces.get(id);
	}

}
