package me.casper.wexo.controllers;

import me.casper.wexo.WEXOApplication;
import me.casper.wexo.api.Entry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;

@Controller
public class WebController {
	
	@GetMapping("/")
	public String index(Model model,
	                    @RequestParam(value = "start", defaultValue = "1") int start,
	                    @RequestParam(value = "end", defaultValue = "100") int end,
	                    @RequestParam(value = "genre", defaultValue = "all") String genre,
	                    @RequestParam(value = "type", defaultValue = "all") String type
	) {
		
		ArrayList<Entry> entries = WEXOApplication.getRestInstance().getActiveCache(start, end, genre, type);
		
		HashMap<String, Integer> genres = new HashMap<>();
		HashMap<String, String> coverArt = new HashMap<>(); // A list of URLs to use as cover art for each genre. (Genre -> URL)
		
		if (entries == null || entries.isEmpty()) {
			
			model.addAttribute("cause", "Der blev ikke fundet noget data i vores system!");
			
			return "error";
		}
		
		// For each entry, add the genre to the list of genres if it isn't already there.
		for (Entry entry : entries) {
			
			// For each genre in the entry (there can be multiple) add it to the HashMap.
			for (String entryGenre : entry.getGenres()) {
				
				// If the genre already exists, increment the count, otherwise add it to the map.
				if (genres.containsKey(entryGenre)) {
					
					genres.put(entryGenre, genres.get(entryGenre) + 1);
					
				} else {
					
					genres.put(entryGenre, 1);
				}
				
				// Add a cover art URL to the map.
				for (String url : entry.getBackdrops().keySet()) {
					
					int width = entry.getBackdrops().get(url).get(0);
					int height = entry.getBackdrops().get(url).get(1);
					
					// If the aspect ratio is 16:9, use it as cover art.
					if (width / height == 16 / 9) {
						
						coverArt.put(entryGenre, url);
						
						break;
					}
				}
			}
		}
		
		model.addAttribute("start", start);
		model.addAttribute("end", end);
		model.addAttribute("genre", genre);
		
		model.addAttribute("entries", entries);
		model.addAttribute("genres", genres);
		model.addAttribute("coverArt", coverArt);
		
		return "index";
	}
	
	@GetMapping("/entry/{id}")
	public String entry(Model model,
	                    @PathVariable(value = "id") String id
	) {
		
		Entry entry = WEXOApplication.getRestInstance().getEntry(id);
		
		if (entry == null) {
			
			model.addAttribute("cause", "Der blev ikke fundet noget data i vores system!");
			
			return "error";
		}
		
		model.addAttribute("entry", entry);
		
		return "entry";
	}
}
