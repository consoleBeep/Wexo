package me.casper.wexo;

import com.google.gson.JsonSyntaxException;
import me.casper.util.Time;
import me.casper.wexo.api.REST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WEXOApplication {
	
	public static final Logger LOGGER = LoggerFactory.getLogger(WEXOApplication.class);
	
	private static REST rest;
	
	public static void main(String[] args) {
		
		if (args.length < 1) {
			
			System.err.println("Please provide a path to store the cache in!");
			
			return;
		}
		
		// Fetch all the shows every 15 minutes on a separate thread.
		Thread updateThread = new Thread(() -> {
			
			// Wait 1 second for the logger to be initialized.
			try {
				
				Thread.sleep(1_000);
				
			} catch (InterruptedException ignored) {
				
				throw new RuntimeException("The update thread was interrupted!");
			}
			
			// 15 minutes in milliseconds.
			final int updateInterval = 900_000;
			
			rest = new REST(args[0]);
			
			while (true) {
				
				final long startTime = System.currentTimeMillis();
				
				try {
					
					// If our cache is up-to-date, and the cache size isn't less than the total items, we don't need to update it.
					if (rest.getLastUpdated() > (System.currentTimeMillis() - updateInterval) && !rest.getActiveCache().isEmpty()) {
						
						LOGGER.info("The cache is up to date, skipping update...");
						
						Thread.sleep(updateInterval);
						
						continue;
					}
					
					LOGGER.info("Updating cache data...");
					
					for (int i = 1; i < REST.TOTAL_ITEMS; i += REST.MAX_ITEMS_PER_REQUEST) {
						
						final int end = i + REST.MAX_ITEMS_PER_REQUEST - 1;
						
						LOGGER.info("Fetching item indicies from {} to {}...", i, end);
						
						// Fetch the items from the API and write them to the cache.
						rest.fetch(i, end);
						rest.write();
					}
					
					LOGGER.info("Cache data updated in {}!", Time.formatTime(System.currentTimeMillis() - startTime));
					
					Thread.sleep(updateInterval);
					
				} catch (NullPointerException | JsonSyntaxException | ClassCastException e) {
					
					LOGGER.error("Failed to update cache data!", e);
					
				} catch (InterruptedException e) {
					
					throw new RuntimeException("The update thread was interrupted!");
				}
			}
		});
		
		updateThread.setName("Update Thread");
		updateThread.setUncaughtExceptionHandler((t, e) -> LOGGER.error("Uncaught exception in thread {}, the thread has been stopped!", t.getName(), e));
		updateThread.setDaemon(true);
		updateThread.start();
		
		// Start the Spring Application on the main thread.
		SpringApplication.run(WEXOApplication.class, args);
	}
	
	public static REST getRestInstance() {
		
		return rest;
	}
}
