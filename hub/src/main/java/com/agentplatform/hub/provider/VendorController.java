package com.agentplatform.hub.provider;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendors")
public class VendorController {

	@GetMapping
	public List<VendorCatalog.Preset> list() {
		return VendorCatalog.ALL;
	}

}
