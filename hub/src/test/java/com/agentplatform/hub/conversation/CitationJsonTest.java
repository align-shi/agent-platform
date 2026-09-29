package com.agentplatform.hub.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CitationJsonTest {

	@Test
	void roundTripKeepsQuotesAndNewlines() {
		String json = CitationJson.encode(List.of(
				new CitationJson.Item("产品库", "手册.txt", "第一行\"说明\"\n第二行")));
		List<ConversationDtos.CitationView> citations = CitationJson.decode(json);
		assertEquals(1, citations.size());
		assertEquals("产品库", citations.get(0).knowledgeBase());
		assertEquals("手册.txt", citations.get(0).document());
		assertEquals("第一行\"说明\"\n第二行", citations.get(0).content());
	}

	@Test
	void blankDecodesToEmpty() {
		assertTrue(CitationJson.decode(null).isEmpty());
		assertTrue(CitationJson.decode("").isEmpty());
		assertEquals("", CitationJson.encode(List.of()));
	}

}
