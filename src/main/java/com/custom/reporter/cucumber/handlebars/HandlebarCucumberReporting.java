package com.custom.reporter.cucumber.handlebars;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.Base64;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;


@Mojo(name="reporting")
public class HandlebarCucumberReporting extends AbstractMojo
{
	/**
     * The path to the Cucumber JSON files.
     */
    @Parameter(property = "reporting.sourceJsonReportDirectory", required = true)
    private String sourceJsonReportDirectory = "";

    /**
     * The location of the generated report.
     */
    @Parameter(property = "reporting.generatedHtmlReportDirectory", required = true)
    private String generatedHtmlReportDirectory = "";

    @Parameter(property = "reporting.sourceTemplateFile", required = true)
    private String sourceTemplateFile = "";
    
    public void execute() throws MojoExecutionException
    {   
    	var logger = getLog();
    	logger.info(sourceJsonReportDirectory);
    	
    	File templateFile = new File(sourceTemplateFile);
    	if(!templateFile.exists() || templateFile.isDirectory()) { 
    	    throw new MojoExecutionException("Template File Not Found: " + sourceTemplateFile);
    	}
    	logger.info("Template File Exists");
    	
    	File[] dataFiles = new File(sourceJsonReportDirectory).listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
    	
    	logger.info("JSON Report files");
    	logger.info(Integer.toString(dataFiles.length));
    	for(File dataFile:dataFiles) {
    		logger.info(dataFile.getName());
    	}
    	
    	getLog().info(templateFile.getParent());
    	
    	TemplateLoader tl = new FileTemplateLoader(templateFile.getParent());
    	tl.setSuffix("");
    	Handlebars handlebars = new Handlebars(tl);
    	handlebars = helpers(handlebars);
    	
    	String reportData;
  		try {
    		reportData = mergeJsonReports(dataFiles);
		} catch (IOException e) {
			throw new MojoExecutionException("Error in reading JSON Result File: " + sourceJsonReportDirectory + "\\report_cashback.json Exception: " + e.getMessage());
		}

    	Gson gson = new Gson();
		Type type = new TypeToken<Map<String, Object>>(){}.getType();
		Map<String, Object> map = gson.fromJson(reportData, type);   
		
		logger.info("Data converted to GSON");
		Context context = Context.newBuilder(map).build();
		logger.info("Context set");
		
		Template template;
		try {
			template = handlebars.compile(templateFile.getName());
			logger.info("Template compiled");
			
			String report = template.apply(context);			
			logger.info("Template converted to report");
			
			new File(generatedHtmlReportDirectory).mkdirs();
			BufferedWriter writer = new BufferedWriter(new FileWriter(generatedHtmlReportDirectory + "\\report.html"));
			logger.info("Report file bufferedWriter created");
		    writer.write(report);
		    writer.close();
		    logger.info("Report written to file: " );
		} catch (IOException e) {
			throw new MojoExecutionException("Error in creating report: " + generatedHtmlReportDirectory + "\\report.html Exception: " + e.getMessage());
		}	
    	    	
    }
    
    public Handlebars helpers(Handlebars handlebars) {
		handlebars.registerHelper("cleanid", new Helper<Object>() {
		    public Object apply(Object value, Options options) {
		    	return ((String)value).replaceAll("[^a-zA-Z0-9-_]*", "");
		    }
		  });
		
		handlebars.registerHelper("duration", new Helper<Object>() {
		    public Object apply(Object value, Options options) {
		    	Duration d = Duration.ofNanos(Math.round((Double)value));
		    	return d.toSeconds() + "s " + d.toMillisPart() + "ms";
		    }
		  });
		
		ConditionalHelpers[] helpers = ConditionalHelpers.values();
	    for (ConditionalHelpers helper : helpers) {
	    	handlebars.registerHelper(helper.name(), helper);
	    }
	    
	    handlebars.registerHelper("embedmime", new Helper<Object>() {
		    public Object apply(Object value, Options options) {
		    	String htmlstr = "<span>";
		    	String str = (String)value;
		    	byte[] b = Base64.decodeBase64(str.getBytes(StandardCharsets.UTF_8));
		    	htmlstr += new String(b) + "</span>";
		    	getLog().info(htmlstr);
		    	return htmlstr;
		    }
		  });
		
		return handlebars;
	}
    
    // Merge multiple json report files into a single json oject
    // Each json file is an array of objects. Add all objects into a single array
    // Create a top level object with single property "features" = array of objects
    public String mergeJsonReports(File[] dataFiles) throws IOException {
    	JsonArray allFeatures = new JsonArray();
    	
    	for(File dataFile: dataFiles) {
    		String reportData = Files.lines(Paths.get(dataFile.toURI())).collect(Collectors.joining());
    		JsonElement jsElem = new JsonParser().parse(reportData);
    		
    		// If root level is not an array, then file is not proper, ignore it
    		if (jsElem.isJsonArray()) {
    			JsonArray features = jsElem.getAsJsonArray();
    			features.forEach((jsEl) -> allFeatures.add(jsEl));
    		}
    	}
    	
    	JsonObject base = new JsonObject();
		base.add("features", allFeatures);
		return new Gson().toJson(base);
    }
}
