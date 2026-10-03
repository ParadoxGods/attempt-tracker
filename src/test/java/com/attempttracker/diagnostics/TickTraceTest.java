package com.attempttracker.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.util.Filepath;
import org.junit.Test;

public class TickTraceTest
{
	@Test
	public void NewColumnsAlignAndPreserveCallbackEvidenceThroughFilepathWriter() throws Exception
	{
		StringWriter content = new StringWriter();
		Filepath directory = mock(Filepath.class), file = mock(Filepath.class);
		when(directory.join(anyString())).thenReturn(file);
		when(file.openBufferedWriter(StandardOpenOption.CREATE_NEW)).thenReturn(new BufferedWriter(content));
		FishingFrameTrace events = new FishingFrameTrace();
		events.record(100, "interaction", 0); events.record(100, "hit", 16, 0); events.record(101, "animation", 618, 0);
		String row = TickTrace.row(9, 618, 2, "FISHING", "spot", 0, 100, 1, 2, 0, 0, true, 3,
			"COUNTED", "spot, \"numeric\"", "", "harpoon", 9, 1, 0, "2t", "timer=0", events.events(), true);
		try (TickTrace trace = new TickTrace(directory)) { trace.append(row); }
		String[] lines = content.toString().split("\\r\\n");
		List<String> header = csvColumns(lines[0]), values = csvColumns(lines[1]);
		assertEquals(25, header.size()); assertEquals(header.size(), values.size());
		assertEquals("two_tick_state", header.get(22)); assertEquals("timer=0", values.get(22));
		assertEquals("callback_events", header.get(23)); assertEquals(events.events(), values.get(23));
		assertEquals("events_truncated", header.get(24)); assertEquals("true", values.get(24));
		assertEquals("spot, \"numeric\"", values.get(15));
		assertFalse(values.get(23).contains("harpoon"));
	}

	@Test
	public void LegacyRowOverloadsDefaultNewColumnsToEmptyAndNotTruncated()
	{
		String oldest = TickTrace.row(1, -1, 0, "FISHING", "spot", 0, 100, 3, 5, 4, 0, false, 0, "NONE", "", "", "", -1);
		String adaptive = TickTrace.row(1, -1, 0, "FISHING", "spot", 0, 100, 3, 5, 4, 0, false, 0, "NONE", "", "", "", -1, 0, 0, "");
		String twoTick = TickTrace.row(1, -1, 0, "FISHING", "spot", 0, 100, 3, 5, 4, 0, false, 0, "NONE", "", "", "", -1, 0, 0, "", "state");
		for (String row : new String[]{oldest, adaptive, twoTick})
		{
			List<String> fields = csvColumns(row.trim()); assertEquals(25, fields.size());
			assertEquals("", fields.get(23)); assertEquals("false", fields.get(24)); assertTrue(row.endsWith("\r\n"));
		}
	}

	private static List<String> csvColumns(String line)
	{
		List<String> fields = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
		for (int i = 0; i < line.length(); i++)
		{
			char character = line.charAt(i);
			if (character == '"')
			{
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
				else { quoted = !quoted; }
			}
			else if (character == ',' && !quoted) { fields.add(field.toString()); field.setLength(0); }
			else { field.append(character); }
		}
		fields.add(field.toString()); return fields;
	}
}
