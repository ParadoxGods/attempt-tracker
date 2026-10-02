package com.attempttracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.util.Filepath;

/** Reads test fixtures through the same restricted file API as the production plugin. */
public final class FilepathTestSupport
{
	private FilepathTestSupport() { }

	public static byte[] readBytes(Filepath file) throws IOException
	{
		try (InputStream input = file.openInputStream()) { return input.readAllBytes(); }
	}

	public static String readString(Filepath file) throws IOException
	{
		return new String(readBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}

	public static List<String> readLines(Filepath file) throws IOException
	{
		try (BufferedReader input = file.openBufferedReader()) { return input.lines().collect(Collectors.toList()); }
	}
}
