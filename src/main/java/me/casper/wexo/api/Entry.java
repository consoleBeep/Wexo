package me.casper.wexo.api;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
public class Entry {
	
	private final String id;
	
	private final String title;
	private final String description;
	private final String programType;
	
	private final int releaseYear;
	
	private final HashMap<String, List<Integer>> covers;
	private final HashMap<String, List<Integer>> backdrops;
	
	private final ArrayList<String> genres;
	
	private final ArrayList<String> actors;
	private final ArrayList<String> directors;
	
	private final ArrayList<String> trailers;
}
