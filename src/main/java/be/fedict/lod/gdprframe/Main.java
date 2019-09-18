/*
 * Copyright (c) 2018, Bart Hanssens
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package be.fedict.lod.gdprframe;

import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Converts a JSON-LD file to a more human friendly JSON-LD using "frames".
 * 
 * @see <a href="https://json-ld.org/spec/latest/json-ld-framing/">https://json-ld.org/spec/latest/json-ld-framing/</a>
 * @author Bart Hanssens
 */
public class Main {
	private final static Logger LOG = LoggerFactory.getLogger(Main.class);

	private final static Options OPTS = new Options()
		.addRequiredOption("i", "infile", true, "input file")
		.addRequiredOption("f", "frame", true, "jsonld frame")
		.addRequiredOption("o", "outfile", true, "output file");

	/**
	 * Print help info
	 */
	private static void printHelp() {
		HelpFormatter fmt = new HelpFormatter();
		fmt.printHelp("tagger", OPTS);
	}

	/**
	 * Parse command line arguments
	 *
	 * @param args command line arguments
	 * @return CLI parser
	 */
	private static CommandLine parse(String[] args) {
		CommandLineParser cli = new DefaultParser();
		try {
			return cli.parse(OPTS, args);
		} catch (ParseException ex) {
			printHelp();
		}
		return null;
	}

	/**
	 * Convert JSON-LD file using JSON-LD Frame
	 * 
	 * @param infile input file
	 * @param inframe frame file
	 * @param outfile output file
	 */
	private static void convert(Path infile, Path inframe, Path outfile) throws IOException, JsonLdError {
		Object obj;
		Object frame;
		
		// read input file and JSON input frame
		try(BufferedReader r = Files.newBufferedReader(infile);
			BufferedReader rf = Files.newBufferedReader(inframe)) {
		
			// merge all graphs for buggy JSON-LD-Java versions (0.12.1)
			// might not be needed anymore when fixed in JSON-LD-Java
			Model parsed = Rio.parse(r, "", RDFFormat.JSONLD);
			Model merged = new LinkedHashModel();
			parsed.forEach(s -> merged.add(s.getSubject(), s.getPredicate(), s.getObject()));
			parsed.clear();
			StringWriter w = new StringWriter();
			Rio.write(merged, w, RDFFormat.JSONLD);
			// obj = JsonUtils.fromReader(r);
			obj = JsonUtils.fromString(w.toString());
			frame = JsonUtils.fromReader(rf);
		}

		// set JSON-LD options
		JsonLdOptions opts = new JsonLdOptions();
		opts.setOmitDefault(Boolean.TRUE);
		opts.setProcessingMode(JsonLdOptions.JSON_LD_1_1);
	
		// convert the JSON-LD with frame to a more human readable shape
		Map<String,Object> res = JsonLdProcessor.frame(obj, frame, opts);
		List lst = (List) res.get(JsonLdConsts.GRAPH);
		lst.forEach(l -> System.err.println(((Map) l).keySet()));

		// write the file
		try(BufferedWriter w = Files.newBufferedWriter(outfile, 
											StandardOpenOption.CREATE, 
											StandardOpenOption.TRUNCATE_EXISTING)) {
			JsonUtils.writePrettyPrint(w, res);
		}
	}
	
	/**
	 * Main
	 * 
	 * @param args 
	 */
	public static void main(String[] args) {
		CommandLine cli = parse(args);
		if (cli == null) {
			LOG.error("Could not parse command line options");
			System.exit(-1);
		}
			
		Path infile = Paths.get(cli.getOptionValue("i"));
		Path inframe = Paths.get(cli.getOptionValue("f"));
		Path outfile = Paths.get(cli.getOptionValue("o"));
	
		LOG.info("Converting {} with frame {} to {}", infile, inframe, outfile);
		
		try {
			convert(infile, inframe, outfile);
		} catch (Exception ioe) {
			LOG.error("Error processing", ioe);
			System.exit(-2);
		}
	}
}
