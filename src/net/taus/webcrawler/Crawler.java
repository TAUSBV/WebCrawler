package net.taus.webcrawler;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import net.taus.webcrawler.filter.Filter;
import net.taus.webcrawler.filter.URLFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

public class Crawler {
	
	static Properties prop = null;	
	static ThreadPoolExecutor executor = null;
	static HashSet<String> URLs_ToVisit_keys;
	static HashSet<String> URLs_Visited;
	static List<Link> URLs_ToVisit;
	static URLFilter urlFilters;
	
	static BrowserProxy proxy = null;

	static FSDirectory index = null;
	static IndexWriter indexWriter = null;

	static boolean retry = false;

	public static void main(String args[]) throws Exception {
		
		readCommandLineArguments(args);		
		executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Integer.parseInt(prop.getProperty("crawl.threads", "1").trim()));				
		initializeFilters();
		initializeLucene();
		initializeURLs();
		initializeBrowserProxy();
		
		int initialTasks = URLs_ToVisit.size();

		for(int i=0;i<initialTasks;i++) {
			Link todo = URLs_ToVisit.remove(URLs_ToVisit.size()-1);
			URLs_ToVisit_keys.remove(todo.getURL());
			submitTask(todo);
		}

		while(true) {
			Thread.sleep(100);
			if(executor.getCompletedTaskCount()<executor.getTaskCount() || URLs_ToVisit.size()!=0) {
				if(URLs_ToVisit.size()!=0) {
					Link todo = URLs_ToVisit.remove(URLs_ToVisit.size()-1);
					URLs_ToVisit_keys.remove(todo.getURL());
					submitTask(todo);
				}
			} else {
				System.out.println("No more URLs to visit");
				break;
			}
		}

		close();
		
	}
	
	public static void readCommandLineArguments(String args[]) throws ParseException, FileNotFoundException, IOException {
		Options options = new Options();
		options.addOption("c", "config-file", true, "Path to the configuration file, [default crawler.properties]");
		options.addOption("r", "retry-errors", true, "Retry urls with errors in previous runs");
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);
		String propertiesFile = cmd.getOptionValue("c", "crawler.properties");
		Crawler.retry = Boolean.parseBoolean(cmd.getOptionValue("r", "false"));
		prop = new Properties();
		prop.load(new FileReader(propertiesFile));		
	}
	
	public static void initializeURLs() {
		URLs_ToVisit_keys = new HashSet<>();
		URLs_ToVisit = new ArrayList<>();
		try {
			URLs_ToVisit = Crawler.getURLsToVisit();
		} catch (IOException e) {
			URLs_ToVisit = new ArrayList();
		}
		try {
			URLs_Visited = Crawler.getVisitedURLs();
		} catch (IOException e) {
			URLs_Visited = new HashSet();
		}
		if (URLs_ToVisit.isEmpty()) {
			if (prop.containsKey("crawl.starting.url.list")) {
				Arrays.stream(prop.getProperty("crawl.starting.url.list").split("\\s"))
						.forEach(url -> {
							String u = url.trim();
							URLs_ToVisit_keys.add(u);
							URLs_ToVisit.add(new Link(u, 0));
						});
			} else {
				String u = prop.getProperty("crawl.starting.url").trim();
				Link first = new Link(u, 0);
				URLs_ToVisit_keys.add(u);
				URLs_ToVisit.add(first);
				persistURLStatus(first, "QUEUED");
			}
		}
	}
	
	public static void initializeBrowserProxy() {
		proxy = new BrowserProxy();
		proxy.start(0);
		proxy.setConnectTimeout(Integer.parseInt(prop.getProperty("browser.timeout", "30")), TimeUnit.SECONDS);
		proxy.addShortCircuitRequestFilter();			
	}

	public static void initializeLucene() throws IOException {
		index = FSDirectory.open(Paths.get(prop.getProperty("crawl.index.folder", "indexes")));
		indexWriter = new IndexWriter(index, new IndexWriterConfig());
	}
	
	@SuppressWarnings("unchecked")
	public static void initializeFilters() throws Exception {
		urlFilters = new URLFilter();
		
		for(String filter : prop.getProperty("crawl.url.filter", "").split("\\|")) {
			Class<?> filterClass = Class.forName(prop.getProperty("crawl.url.filter." + filter + ".class"));
			Filter<String> filterObject = null;
			if(prop.containsKey("crawl.url.filter." + filter + ".parameters")) {
				Properties filterProp = new Properties();
				for(String parameter : prop.getProperty("crawl.url.filter." + filter + ".parameters").split("\\|")) {
					filterProp.put(parameter, prop.getProperty("crawl.url.filter." + filter + "." + parameter));
				}
				Constructor<?> ctor = filterClass.getConstructor(Properties.class);
				filterObject = (Filter<String>)ctor.newInstance(filterProp);
			} else {
				Constructor<?> ctor = filterClass.getConstructor();
				filterObject = (Filter<String>)ctor.newInstance();
			}
			urlFilters.addFilter(filter, filterObject);
		}		
	}
	
	public static String getProperty(String key, String def) {
		return prop.getProperty(key, def);
	}
	
	public static void submitTask(Link link) {
		if(Crawler.urlFilters.execute(link.URL)) {		
			executor.submit(() -> {			
				Browser browser = new Browser();
				browser.init(prop, proxy);
				try {
					browser.browse(link);
					URLs_Visited.add(link.getURL());
					Crawler.persistURLStatus(link, "SUCCESS");
				} catch (Exception e) {
					e.printStackTrace();
					Crawler.persistURLStatus(link, "ERROR");
				}
				browser.finalize();
			});
		} else {
			//System.out.println(link.URL + " filtered out.");
		}
	}

	public static void addLink(Link link) {
		synchronized(Crawler.URLs_ToVisit) {
			if(Crawler.urlFilters.execute(link.URL)) {
				if(!(Crawler.URLs_Visited.contains(link.URL) || Crawler.URLs_ToVisit_keys.contains(link.URL))) {
					System.out.println("new URL: " + link.URL);
					Crawler.URLs_ToVisit_keys.add(link.URL);
					Crawler.URLs_ToVisit.add(link);
					Crawler.persistURLStatus(link, "QUEUED");
				} else {
					//System.out.println(link.URL + " visited already / or in the queue.");
				}
			} else {
				//System.out.println(link.URL + " filtered out.");
			}
		}
	}

	public static void persistURLStatus(Link link, String status) {
		Document doc = new Document();
		doc.add(new StoredField("DATE", new Date().toString()));
		FieldType f = new FieldType();
		f.setOmitNorms(false);
		f.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
		f.setStoreTermVectors(true);
		f.setStored(true);
		f.setTokenized(false);
		doc.add(new Field("URL", link.getURL(), f));
		doc.add(new Field("STATUS", status, f));
		try {
			indexWriter.updateDocument(new Term("URL", link.getURL()), doc);
			indexWriter.commit();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static HashSet<String> getVisitedURLs() throws IOException {
		DirectoryReader reader = DirectoryReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);
		Term term = new Term("STATUS", "SUCCESS");
		Query query = new TermQuery(term);
		TopDocs docs = searcher.search(query, Integer.MAX_VALUE);
		HashSet<String> urls = new HashSet<String>();
		for (ScoreDoc scoreDoc : docs.scoreDocs) {
			Document doc = searcher.doc(scoreDoc.doc);
			String url = doc.get("URL");
			urls.add(url);
		}
		return urls;
	}

	public static List<Link> getURLsToVisit() throws IOException {
		DirectoryReader reader = DirectoryReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);
		Term term = new Term("STATUS", "QUEUED");
		Query query = new TermQuery(term);
		TopDocs docs = searcher.search(query, Integer.MAX_VALUE);
		List<Link> urls = new ArrayList<>();
		for (ScoreDoc scoreDoc : docs.scoreDocs) {
			Document doc = searcher.doc(scoreDoc.doc);
			String url = doc.get("URL");
			urls.add(new Link(url, 0));
		}
		if(Crawler.retry) {
			term = new Term("STATUS", "ERROR");
			query = new TermQuery(term);
			docs = searcher.search(query, Integer.MAX_VALUE);
			for (ScoreDoc scoreDoc : docs.scoreDocs) {
				Document doc = searcher.doc(scoreDoc.doc);
				String url = doc.get("URL");
				urls.add(new Link(url, 0));
			}
		}
		return urls;
	}

	public static void close() throws IOException {
		proxy.stop();
		indexWriter.close();
		index.close();
		executor.shutdown();
	}

}
