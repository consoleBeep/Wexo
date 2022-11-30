package me.casper.wexo.api;

import com.google.gson.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static me.casper.wexo.WEXOApplication.LOGGER;

public class REST {
	
	public static final int TOTAL_ITEMS = 10_000;
	public static final int MAX_ITEMS_PER_REQUEST = 1_000;
	
	public static final String BASE_URL = "https://feed.entertainment.tv.theplatform.eu/f/jGxigC/bb-all-pas?form=json&lang=da";
	
	private final ArrayList<Entry> activeCache = new ArrayList<>();
	private final ArrayList<Entry> fallbackCache = new ArrayList<>();
	private final File cacheFile;
	private long lastUpdated = 0;
	
	public REST(String cachePath) {
		
		cacheFile = new File(cachePath);
		
		try {
			
			// If the cache file doesn't exist, create it.
			if (!cacheFile.exists()) {
				
				LOGGER.warn("Cache file doesn't exist, creating it...");
				
				// Make sure the parent directories exist.
				cacheFile.getParentFile().mkdirs();
				
				if (!cacheFile.createNewFile()) {
					
					LOGGER.error("Failed to create cache file, exiting...");
					
					System.exit(1);
				}
			}
			
			// Parse the fallback data into the fallback cache.
			LOGGER.info("Loading fallback cache data...");
			
			Gson gson = new Gson();
			
			String data = new String(Files.readAllBytes(cacheFile.toPath()));
			
			if (data.isEmpty()) {
				
				LOGGER.warn("Cache data is empty, awaiting API response...");
				
				return;
			}
			
			JsonObject wrapper = gson.fromJson(data, JsonObject.class);
			
			JsonPrimitive lastUpdated = wrapper.getAsJsonPrimitive("lastUpdated");
			JsonArray entries = wrapper.getAsJsonArray("entries");
			
			for (int i = 0; i < entries.size(); i++) {
				
				JsonElement rawEntry = entries.get(i);
				
				if (rawEntry == null || rawEntry.isJsonNull())
					continue;
				
				JsonObject entryObject = rawEntry.getAsJsonObject();
				
				Entry entry = defineCachedEntry(entryObject);
				
				if (entry == null) continue;
				
				fallbackCache.add(entry);
			}
			
			this.lastUpdated = lastUpdated.getAsLong();
			
		} catch (Exception e) {
			
			LOGGER.error("Failed to parse cache data from disk, exiting...", e);
			
			System.exit(1);
		}
		
		// Copy the fallback cache into the active cache.
		activeCache.addAll(fallbackCache);
	}
	
	public void fetch(int from, int to) {
		
		final String range = String.format("&range=%d-%d", from, to);
		
		OkHttpClient client =
				new OkHttpClient.Builder()
						.connectTimeout(Duration.ofSeconds(10))
						.readTimeout(Duration.ofMinutes(1))
						.build();
		
		Request request =
				new Request.Builder()
						.addHeader("Accept-Encoding", "gzip")
						.url(BASE_URL + range)
						.build();
		
		try (Response response = client.newCall(request).execute()) {
			
			if (response.code() != 200) {
				
				LOGGER.error("Failed to fetch data from API! (Status Code: {})", response.code());
				
				return;
			}
			
			// The response is compressed with GZIP, so we need to decompress it.
			StringBuilder data = new StringBuilder();
			
			GZIPInputStream inputStream = new GZIPInputStream(response.body().byteStream());
			
			byte[] buffer = new byte[1024];
			int length;
			while ((length = inputStream.read(buffer)) != -1) {
				
				data.append(new String(buffer, 0, length));
			}
			
			inputStream.close();
			
			JsonArray entries = new Gson().fromJson(data.toString(), JsonObject.class).getAsJsonArray("entries");
			
			if (entries == null || entries.isEmpty()) {
				
				LOGGER.error("Failed to fetch data from API! (No Entries Found)");
				
				return;
			}
			
			for (int i = 0; i < entries.size(); i++) {
				
				JsonObject entryObject = entries.get(i).getAsJsonObject();
				
				Entry entry = defineEntry(entryObject);
				
				if (entry == null || activeCache.contains(entry))
					continue;
				
				activeCache.add(entry);
			}
			
		} catch (Exception e) {
			
			LOGGER.error("Failed to fetch data from API!", e);
		}
	}
	
	public void write() {
		
		// If the active cache is empty, don't write anything.
		if (activeCache.isEmpty()) return;
		
		// Write the active cache to disk.
		Gson gson = new Gson();
		
		JsonElement tree = gson.toJsonTree(activeCache);
		
		if (tree == null || tree.isJsonNull())
			return;
		
		final long now = System.currentTimeMillis();
		
		JsonObject wrapper = new JsonObject();
		
		wrapper.add("lastUpdated", new JsonPrimitive(now));
		wrapper.add("entries", tree);
		
		try {
			
			Files.write(cacheFile.toPath(), gson.toJson(wrapper).getBytes());
			
			lastUpdated = now;
			
			// Copy the active cache into the fallback cache.
			for (Entry entry : activeCache) {
				
				if (fallbackCache.contains(entry)) continue;
				
				fallbackCache.add(entry);
			}
			
		} catch (IOException e) {
			
			LOGGER.error("Failed to write cache to disk!", e);
		}
	}
	
	public Entry getEntry(String id) {
		
		for (Entry entry : activeCache) {
			
			if (entry.getId().equals(id)) return entry;
		}
		
		return null;
	}
	
	public ArrayList<Entry> getActiveCache() {
		
		return activeCache;
	}
	
	public ArrayList<Entry> getActiveCache(int from, int to, String genre, String type) {
		
		// Make sure the range is valid.
		if (from < 0 || to < 0 || from > to)
			return null;
		
		// Make sure the range is within the cache.
		to = Math.min(to, activeCache.size());
		
		ArrayList<Entry> filteredCache = new ArrayList<>();
		
		// If the genre and the type are both "all", just return the range.
		if (genre.equalsIgnoreCase("all") && type.equalsIgnoreCase("all")) {
			
			for (int i = from; i < to; i++) {
				
				filteredCache.add(activeCache.get(i));
			}
			
			return filteredCache;
		}
		
		// If the genre is "all", filter by the type.
		if (genre.equalsIgnoreCase("all")) {
			
			for (int i = from; i < to; i++) {
				
				Entry entry = activeCache.get(i);
				
				if (filteredCache.contains(entry)) continue;
				
				if (entry.getProgramType().equalsIgnoreCase(type))
					filteredCache.add(entry);
			}
			
			return filteredCache;
		}
		
		// If the type is "all", filter by the genre.
		if (type.equalsIgnoreCase("all")) {
			
			for (int i = from; i < to; i++) {
				
				Entry entry = activeCache.get(i);
				
				if (filteredCache.contains(entry)) continue;
				
				if (entry.getGenres().contains(genre))
					filteredCache.add(entry);
			}
			
			return filteredCache;
		}
		
		// Filter by both the genre and the type.
		for (int i = from; i < to; i++) {
			
			Entry entry = activeCache.get(i);
			
			if (filteredCache.contains(entry)) continue;
			
			if (entry.getGenres().contains(genre) && entry.getProgramType().equalsIgnoreCase(type))
				filteredCache.add(entry);
		}
		
		return filteredCache;
	}
	
	public ArrayList<Entry> getFallbackCache() {
		
		return fallbackCache;
	}
	
	public long getLastUpdated() {
		
		return lastUpdated;
	}
	
	public File getCacheFile() {
		
		return cacheFile;
	}
	
	private Entry defineEntry(JsonObject entry) {
		
		JsonElement rawId = entry.get("guid");
		
		JsonElement rawTitle = entry.get("title");
		JsonElement rawDescription = entry.get("description");
		JsonElement rawProgramType = entry.get("plprogram$programType");
		
		JsonElement rawReleaseYear = entry.get("plprogram$year");
		
		String id = rawId == null || rawId.isJsonNull() ? "N/A" : rawId.getAsString();
		
		String title = rawTitle == null || rawTitle.isJsonNull() ? "N/A" : rawTitle.getAsString();
		String description = rawDescription == null || rawDescription.isJsonNull() ? "N/A" : rawDescription.getAsString();
		String programType = rawProgramType == null || rawProgramType.isJsonNull() ? "N/A" : rawProgramType.getAsString();
		
		int releaseYear = rawReleaseYear == null || rawReleaseYear.isJsonNull() ? -1 : rawReleaseYear.getAsInt();
		
		HashMap<String, List<Integer>> covers = new HashMap<>();
		HashMap<String, List<Integer>> backdrops = new HashMap<>();
		
		ArrayList<String> genres = new ArrayList<>();
		
		ArrayList<String> actors = new ArrayList<>();
		ArrayList<String> directors = new ArrayList<>();
		
		ArrayList<String> trailers = new ArrayList<>();
		
		// Handle the covers and backdrops.
		JsonObject thumbnails = entry.getAsJsonObject("plprogram$thumbnails");
		
		if (thumbnails == null || thumbnails.isJsonNull() || thumbnails.size() == 0)
			return null;
		
		// For each thumbnail, check if it's a cover or a backdrop.
		// "thumbnails" is a nested object, so we need to iterate over the keys.
		for (String key : thumbnails.keySet()) {
			
			JsonObject thumbnail = thumbnails.getAsJsonObject(key);
			
			String url = thumbnail.get("plprogram$url").getAsString();
			
			int width = thumbnail.get("plprogram$width").getAsInt();
			int height = thumbnail.get("plprogram$height").getAsInt();
			
			if (url.contains("po") || url.contains("Poster"))
				covers.put(url, List.of(width, height));
			
			else if (url.contains("bd"))
				backdrops.put(url, List.of(width, height));
		}
		
		// Handle the genres.
		JsonArray tags = entry.getAsJsonArray("plprogram$tags");
		
		if (tags == null || tags.isJsonNull() || tags.size() == 0)
			return null;
		
		for (int i = 0; i < tags.size(); i++) {
			
			JsonObject tag = tags.get(i).getAsJsonObject();
			
			if (!tag.get("plprogram$scheme").getAsString().equalsIgnoreCase("genre"))
				continue;
			
			String genre = tag.get("plprogram$title").getAsString();
			
			genres.add(genre);
		}
		
		// Handle the actors and directors.
		JsonArray credits = entry.getAsJsonArray("plprogram$credits");
		
		if (credits == null || credits.isJsonNull() || credits.size() == 0)
			return null;
		
		for (int i = 0; i < credits.size(); i++) {
			
			JsonObject credit = credits.get(i).getAsJsonObject();
			
			String type = credit.get("plprogram$creditType").getAsString();
			String name = credit.get("plprogram$personName").getAsString();
			
			if (type.equalsIgnoreCase("actor")) actors.add(name);
			else if (type.equalsIgnoreCase("director")) directors.add(name);
		}
		
		// Handle the trailers.
		JsonArray media = entry.getAsJsonArray("plprogramavailability$media");
		
		if (media == null || media.isJsonNull() || media.size() == 0)
			return null;
		
		for (int i = 0; i < media.size(); i++) {
			
			JsonObject mediaObject = media.get(i).getAsJsonObject();
			
			JsonElement rawUrl = mediaObject.get("plmedia$publicUrl");
			
			if (rawUrl == null || rawUrl.isJsonNull() || rawUrl.getAsString().isEmpty())
				continue;
			
			String url = rawUrl.getAsString();
			
			trailers.add(url);
		}
		
		return new Entry(id, title, description, programType, releaseYear, covers, backdrops, genres, actors, directors, trailers);
	}
	
	private Entry defineCachedEntry(JsonObject entry) {
		
		JsonElement rawId = entry.get("id");
		
		JsonElement rawTitle = entry.get("title");
		JsonElement rawDescription = entry.get("description");
		JsonElement rawProgramType = entry.get("programType");
		
		JsonElement rawReleaseYear = entry.get("releaseYear");
		
		String id = rawId == null || rawId.isJsonNull() ? "N/A" : rawId.getAsString();
		
		String title = rawTitle == null || rawTitle.isJsonNull() ? "N/A" : rawTitle.getAsString();
		String description = rawDescription == null || rawDescription.isJsonNull() ? "N/A" : rawDescription.getAsString();
		String programType = rawProgramType == null || rawProgramType.isJsonNull() ? "N/A" : rawProgramType.getAsString();
		
		int releaseYear = rawReleaseYear == null || rawReleaseYear.isJsonNull() ? -1 : rawReleaseYear.getAsInt();
		
		if (id.equals("N/A") || title.equals("N/A") || description.equals("N/A") || programType.equals("N/A") || releaseYear == -1)
			return null;
		
		HashMap<String, List<Integer>> covers = new HashMap<>();
		HashMap<String, List<Integer>> backdrops = new HashMap<>();
		
		ArrayList<String> genres = new ArrayList<>();
		
		ArrayList<String> actors = new ArrayList<>();
		ArrayList<String> directors = new ArrayList<>();
		
		ArrayList<String> trailers = new ArrayList<>();
		
		// Handle the covers and backdrops (convert to HashMap).
		JsonObject coversObject = entry.getAsJsonObject("covers");
		JsonObject backdropsObject = entry.getAsJsonObject("backdrops");
		
		if (coversObject == null || coversObject.isJsonNull() || coversObject.size() == 0)
			return null;
		
		if (backdropsObject == null || backdropsObject.isJsonNull() || backdropsObject.size() == 0)
			return null;
		
		// For each cover, add it to the covers map.
		for (String key : coversObject.keySet()) {
			
			JsonArray cover = coversObject.getAsJsonArray(key);
			
			int width = cover.get(0).getAsInt();
			int height = cover.get(1).getAsInt();
			
			covers.put(key, List.of(width, height));
		}
		
		// For each backdrop, add it to the backdrops map.
		for (String key : backdropsObject.keySet()) {
			
			JsonArray backdrop = backdropsObject.getAsJsonArray(key);
			
			int width = backdrop.get(0).getAsInt();
			int height = backdrop.get(1).getAsInt();
			
			backdrops.put(key, List.of(width, height));
		}
		
		// Handle the genres.
		JsonArray genresArray = entry.getAsJsonArray("genres");
		
		if (genresArray == null || genresArray.isJsonNull() || genresArray.size() == 0)
			return null;
		
		for (int i = 0; i < genresArray.size(); i++) {
			
			String genre = genresArray.get(i).getAsString();
			
			genres.add(genre);
		}
		
		// Handle the actors and directors.
		JsonArray actorsArray = entry.getAsJsonArray("actors");
		JsonArray directorsArray = entry.getAsJsonArray("directors");
		
		if (actorsArray == null || actorsArray.isJsonNull() || actorsArray.size() == 0)
			return null;
		
		if (directorsArray == null || directorsArray.isJsonNull() || directorsArray.size() == 0)
			return null;
		
		for (int i = 0; i < actorsArray.size(); i++) {
			
			String actor = actorsArray.get(i).getAsString();
			
			actors.add(actor);
		}
		
		for (int i = 0; i < directorsArray.size(); i++) {
			
			String director = directorsArray.get(i).getAsString();
			
			directors.add(director);
		}
		
		// Handle the trailers.
		JsonArray trailersArray = entry.getAsJsonArray("trailers");
		
		if (trailersArray == null || trailersArray.isJsonNull() || trailersArray.size() == 0)
			return null;
		
		for (int i = 0; i < trailersArray.size(); i++) {
			
			String trailer = trailersArray.get(i).getAsString();
			
			trailers.add(trailer);
		}
		
		return new Entry(id, title, description, programType, releaseYear, covers, backdrops, genres, actors, directors, trailers);
	}
}
