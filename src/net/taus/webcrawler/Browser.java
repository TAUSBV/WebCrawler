package net.taus.webcrawler;

import com.google.common.io.Files;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClientBuilder;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Browser {

	private WebDriver driver = null;
	private WebDriverWait wait = null;
	private ExpectedCondition<Boolean> load = null;
	private boolean crawlLinks = false;
	private boolean crawlIgnoreQueryString = false;
	private String elementFrame = null;
	private String elementLookup = null;
	private By elementLookup_By = null;
	private String languagesLookup = null;
	private By languagesLookup_By = null;
	private String outputPath = null;
	private String browserBaseLocation = null;
	private String browser = null;
	private String browserBin = null;
	private int crawlDepth = 0;
	private BrowserProxy proxy = null;
	private String agent = null;
	private int timeout = 0;
	private int implicitTimeout = 0;
	private boolean crawlFollowAjax = false;
	
	private String identifier = null;

	public Browser() {
	}

	public void init(Properties prop, BrowserProxy proxy) {

		this.crawlLinks = Boolean.parseBoolean(prop.getProperty("crawl.links", "false"));
		this.crawlIgnoreQueryString = Boolean.parseBoolean(prop.getProperty("crawl.ignore.querystring", "false"));
		this.crawlDepth = Integer.parseInt(prop.getProperty("crawl.depth", "0"));
		
		this.crawlFollowAjax = Boolean.parseBoolean(prop.getProperty("crawl.follow.ajax", "false"));

		this.elementFrame = prop.getProperty("element.frame", null);
		this.elementLookup = prop.getProperty("element.lookup", null);

		if (elementLookup == null) {
			this.elementLookup_By = By.tagName("body");
		} else {
			this.elementLookup_By = By.xpath(this.elementLookup);
		}

		this.languagesLookup = prop.getProperty("element.languages", null);
		if(languagesLookup != null) {
			this.elementLookup_By = By.xpath(this.languagesLookup);
		}

		this.browserBaseLocation = prop.getProperty("browser.base.location");
		this.browser = prop.getProperty("browser.name");
		this.browserBin = Util.getDriver(this.browserBaseLocation, this.browser);

		System.out.println(this.browserBin);

		this.outputPath = prop.getProperty("output.path", ".");

		this.agent = prop.getProperty("crawl.agent", null);

		this.timeout = Integer.parseInt(prop.getProperty("browser.timeout", "30"));
		this.implicitTimeout = Integer.parseInt(prop.getProperty("browser.implicit.timeout", "2"));
		
		if(proxy!=null) {
			this.proxy = proxy;
		}

		try {
			loadBrowser();
		} catch (RuntimeException e) {
			e.printStackTrace();
			return;
		}

	}

	public void browse(Link link) throws Exception {
		String urlString = link.getURL();
		System.out.println("Loading URL: " + urlString);

		Map<String, String> headers = getHeadResponse(urlString);
		if(!headers.get("status").equals("200")) {
			throw new HttpException("Response Code is " + headers.get("status"));
		}

		if(headers.get("Content-Type").toLowerCase().contains("application/pdf")) {
			System.out.println("PDF: " + urlString);
			downloadPDF(urlString);
			return;
		}

		if(!(headers.get("Content-Type").toLowerCase().contains("text")
				|| headers.get("Content-Type").toLowerCase().contains("html"))) {
			return;
		}

		try {
			driver.navigate().to(link.getURL());
			wait.until(load);
		} catch (Exception ex) {
			ex.printStackTrace();
			//proxy.stop();
			driver.quit();
			return;
		}
		
		((JavascriptExecutor)driver).executeScript("this.onResourceRequested = function(request, net) {" + 
				"net.setHeader('crawler-identifier', '" + identifier + "')" + 
				"};");		


		String path = getOutputPath(urlString);

		PrintWriter outText = null;
		PrintWriter outHTML = null;

		File outTextFile = new File(path + ".txt");
		File outHTMLFile = new File(path + ".html");
		Files.createParentDirs(outTextFile);
		Files.touch(outTextFile);
		Files.touch(outHTMLFile);
		outText = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outTextFile), "UTF-8"), true);
		outHTML = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outHTMLFile), "UTF-8"), true);

		try {
			if (this.elementFrame != null) {
				driver.switchTo().frame(driver.findElement(By.xpath(this.elementFrame)));
			}
			List<WebElement> elements = driver.findElements(this.elementLookup_By);
			if(elements.isEmpty()) {
				outText.close();
				outHTML.close();
				return;
			} else {
				for (WebElement element : elements) {
					String text = element.getText();
					outText.println(new String(text.getBytes("UTF8"), "UTF8"));
				}
				String html = driver.getPageSource();
				outHTML.println(new String(html.getBytes("UTF8"), "UTF8"));
			}
		} catch (NoSuchElementException ex) {
			System.out.println("Element " + this.elementLookup_By.toString() + " not found.");
		}

		outText.close();
		outHTML.close();

		HashSet<String> alreadySeenNormal = new HashSet<String>();
		HashSet<String> alreadySeenAjax = new HashSet<String>();
		
		alreadySeenNormal.add(urlString);

		if (this.crawlLinks && link.getDepth() < this.crawlDepth) {
						
			List<WebElement> linksAjax = null;
			List<WebElement> linksNormal = null;

			List<List<WebElement>> extractedLinks = null;
			
			if(this.crawlFollowAjax) {
				proxy.enableShortCircuit(identifier);
				extractedLinks= LinkExtractor.extract(driver, this.elementLookup, this.crawlFollowAjax);
				linksNormal = extractedLinks.get(0);
				linksAjax = extractedLinks.get(1);
				proxy.disableShortCircuit(identifier);
			} else {
				extractedLinks = LinkExtractor.extract(driver, this.elementLookup, this.crawlFollowAjax);
				linksNormal = extractedLinks.get(0);				
			}

			for (WebElement element : linksNormal) {
				try {
					String href = element.getAttribute("href");
					if (crawlIgnoreQueryString) {
						href = LinkExtractor.stripQueryString(href);
					}
					href = LinkExtractor.stripAnchor(href);
					if (href == null || href.equals("") || href.startsWith("javascript") || alreadySeenNormal.contains(href)) {
						continue;
					}
					//System.out.println(href);
					alreadySeenNormal.add(href);
					Crawler.addLink(new Link(href, link.getURL(), link.depth + 1));
				} catch(Exception e) {
					continue;
				}
			}
			
			
			if(this.crawlFollowAjax) {
			
				String currentText = driver.findElement(By.tagName("body")).getText();
				
				Actions actions = new Actions(driver);
				while(true) {
					try {
						for(WebElement element : linksAjax) {
							String href = element.getAttribute("href");
							if(href != null) {
								if (crawlIgnoreQueryString) {
									href = LinkExtractor.stripQueryString(href);
								}
								href = LinkExtractor.stripAnchor(href);
							}
							String link_text = element.getText();
							if(link_text==null || link_text.isEmpty()) continue;
							if(href==null || href.isEmpty()) href = urlString + "--" + link_text;
							if(alreadySeenNormal.contains(href) || alreadySeenAjax.contains(href)) continue;
							alreadySeenAjax.add(href);
							System.out.println("Click :: " + href);
							try {
								actions.moveToElement(element).click(element).build().perform();
								driver.manage().timeouts().implicitlyWait(this.implicitTimeout, TimeUnit.SECONDS);
							} catch (Exception e) {
								continue;
							}
							ArrayList<String> tabs = new ArrayList<String>(driver.getWindowHandles());
							if (tabs.size() > 1) {						
								driver.switchTo().window(tabs.get(1));
								driver.close();
								driver.switchTo().window(tabs.get(0));
								continue;
							}
							wait.until(ExpectedConditions.not(ExpectedConditions.textToBe(By.tagName("body"), currentText)));
							String newText = driver.findElement(By.tagName("body")).getText();
							//System.out.println(StringUtils.difference(currentText, newText).length());
							currentText = newText;	
						}
						break;
					} catch(StaleElementReferenceException e) {
						proxy.enableShortCircuit(identifier);
						extractedLinks = LinkExtractor.extract(driver, this.elementLookup, this.crawlFollowAjax);
						linksNormal = extractedLinks.get(0);
						linksAjax = extractedLinks.get(1);
						proxy.disableShortCircuit(identifier);
						for (WebElement element : linksNormal) {
							String href = element.getAttribute("href");
							if (crawlIgnoreQueryString) {
								href = LinkExtractor.stripQueryString(href);
							}
							href = LinkExtractor.stripAnchor(href);						
							if (href == null || href.equals("") || href.startsWith("javascript") || alreadySeenNormal.contains(href)) {
								continue;
							}
							//System.out.println(href);
							alreadySeenNormal.add(href);
							Crawler.addLink(new Link(href, link.getParent(), link.depth + 1));
						}				
					} catch(Exception e) {
					}
				}
			}
		}

	}

	String getOutputPath(String url) {
		String path = "";
		if (url.startsWith("https://"))
			path = this.outputPath + "/" + url.substring(8);
		else if (url.startsWith("http://"))
			path = this.outputPath + "/" + url.substring(7);
		if (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);
		return path;
	}

	void loadBrowser() {
		if (browser.equalsIgnoreCase("chromedriver")) {
			System.setProperty("webdriver.chrome.driver", this.browserBin);
			DesiredCapabilities caps = DesiredCapabilities.chrome();
			caps.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
			LoggingPreferences logPrefs = new LoggingPreferences();
			logPrefs.enable(LogType.BROWSER, Level.INFO);
			caps.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);			
			if(this.proxy!=null) {
				Proxy seleniumProxy = this.proxy.getSeleniumProxy();
				caps.setCapability(CapabilityType.PROXY, seleniumProxy);
			}
			ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.setAcceptInsecureCerts(true);
			chromeOptions.addArguments("--headless");
			chromeOptions.addArguments("--no-sandbox");
			chromeOptions.addArguments("--ignore-ssl-errors=true");
			chromeOptions.addArguments("--ssl-protocol=any");
			chromeOptions.addArguments("--ignore-certificate-errors");
			if (this.agent != null) {
				chromeOptions.addArguments("--user-agent=" + this.agent);
			}
			chromeOptions.merge(caps);
			driver = new ChromeDriver(chromeOptions);
			
		} else {
			
			System.setProperty("phantomjs.binary.path", this.browserBin);			
			
			DesiredCapabilities caps = new DesiredCapabilities();
			caps.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
			
			LoggingPreferences logPrefs = new LoggingPreferences();
			logPrefs.enable(LogType.BROWSER, Level.INFO);
			caps.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
			
			if(this.proxy!=null) {			
				Proxy seleniumProxy = this.proxy.getSeleniumProxy();
				caps.setCapability(CapabilityType.PROXY, seleniumProxy);
			}			
										
			driver = new PhantomJSDriver(caps);			
		}
		driver.manage().window().setSize(new Dimension(1440, 1080));
		wait = new WebDriverWait(driver, this.timeout);
		load = webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete");
		driver.manage().timeouts().implicitlyWait(this.implicitTimeout, TimeUnit.SECONDS);
		driver.manage().timeouts().pageLoadTimeout(this.timeout, TimeUnit.SECONDS);
		driver.manage().timeouts().setScriptTimeout(this.timeout, TimeUnit.SECONDS);
		
		
		identifier = UUID.randomUUID().toString();
				
	}

	public static Map<String, String> getHeadResponse(String URL) {
		HttpClient client = HttpClientBuilder.create().build();
		HttpHead request = new HttpHead(URL);
		HttpResponse response = null;
		try {
			response = client.execute(request);
		} catch (IOException e) {
			e.printStackTrace();
		}
		String contentType = response.getHeaders("Content-Type")[0].getValue();
		String contentLength = "0";
		String contentLanguage = null;
		try {
			contentLength = response.getHeaders("Content-Length")[0].getValue();
			contentLanguage = response.getHeaders("Content-Language")[0].getValue();
		} catch(Exception e){}
		String code = String.valueOf(response.getStatusLine().getStatusCode());
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", contentType);
		headers.put("Content-Length", contentLength);
		if(contentLanguage!=null) {
			headers.put("Content-Language", contentLanguage);
		}
		headers.put("status", code);
		return headers;
	}

	public void downloadPDF(String pdf) throws IOException {
		URL url = new URL(pdf);
		InputStream in = url.openStream();
		File outputFile = new File(getOutputPath(pdf) + ".pdf");
		Files.createParentDirs(outputFile);
		FileOutputStream fos = new FileOutputStream(outputFile);
		int length = -1;
		byte[] buffer = new byte[1024];// buffer for portion of data from connection
		while ((length = in.read(buffer)) > -1) {
			fos.write(buffer, 0, length);
		}
		fos.close();
		in.close();
		System.out.println("File downloaded");
	}

	@Override
	public void finalize() {
		try {
			driver.quit();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
