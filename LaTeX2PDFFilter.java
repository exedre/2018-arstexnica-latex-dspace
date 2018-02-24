/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.mediafilter;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.Iterator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import java.util.List;
import java.util.ArrayList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.dspace.content.Collection;
import org.dspace.content.Metadatum;
import org.dspace.core.Context;
import org.dspace.content.Item;
import org.dspace.content.Bitstream;
import org.dspace.app.bulkedit.BulkEditChange;
import org.dspace.core.Utils;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;


public class LaTeX2PDFFilter extends MediaFilter {
    private Path tempPath;
        
    public String getFilteredName(String oldFilename)  {
        return oldFilename.replaceFirst(".tex$",".pdf");
    }

    public String getBundleName() {
        return "ORIGINAL";
    }

    public String getFormatString() {
        return "PDF";
    }

    public String getDescription() {
        return "Converted PDF";
    }

    public InputStream getDestinationStream(InputStream source) throws Exception {

        // Crea il file tex da compilare
        File tmpfile = createTempLaTeXFile(source);    
        String inputFilename = tmpfile.toPath().toString();

        // Esegui PDFlatex sul file .tex
        String cmd = "pdflatex -output-format=pdf " + inputFilename ;
        try {
          String line;
          Runtime rt = Runtime.getRuntime();
          Process pr = rt.exec(cmd);
        }
        catch (Exception e) {
          System.out.println("Exception in latex compilation");
        }

        // Raccogli il risultato 
        String output = input.replaceFirst(".tex\$",".pdf");
        File fhi = new File(output);
        byte[] textBytes = Files.readAllBytes(fhi.toPath());
        ByteArrayInputStream destination = new ByteArrayInputStream(textBytes);

        return destination;
    }

    public boolean preProcessBitstream(Context c, Item item, Bitstream source) throws Exception {
        // Metodo eseguito prima di realizzare la conversione, puo' servire ad estrarre i metadati utili
        tempPath = Files.createTempDirectory("latex");
        createTempFile(tempPath.toString(), item);
    }

    public void postProcessBitstream(Context c, Item item, Bitstream generatedBitstream) throws Exception {
       c.commit();
    }

    private File createTempLaTeXFile(InputStream source) throws IOException {
        File f = File.createTempFile("document", ".tex", tempPath);
        f.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(f);    
        byte[] buffer = new byte[1024];
        int len = source.read(buffer);
        while (len != -1) {
            fos.write(buffer, 0, len);
            len = source.read(buffer);
        }
        fos.close();
        return f;
    }
    
    public Map<String, List<String>> getMetadataHash(Item item) {
        Map<String, List<String> > md = new HashMap<>();
        Metadatum[] dcValues = item.getMetadata(Item.ANY, Item.ANY, 
                                               Item.ANY, Item.ANY);
       Set<String> schemas = new HashSet<String>();
       for (Metadatum dcValue : dcValues) {
           schemas.add(dcValue.schema);
       }
       for (String schema : schemas) {
           Metadatum[] dcorevalues = item.getMetadata(schema, 
                                                      Item.ANY, Item.ANY, Item.ANY);
           String dateIssued = null;
           String dateAccessioned = null;
           for (Metadatum dcv : dcorevalues) {
               String qualifier = dcv.qualifier;
               if (qualifier == null) {
                   qualifier = "";
               } else {
                   qualifier = "@" + qualifier;
               }
               String key = schema + "@" + dcv.element + qualifier;
               List<String> vlist = new ArrayList<String>();
               if ( md.containsKey(key) ) {
                   vlist = md.get(key);
               }
               vlist.add(dcv.value);
               md.put(key,vlist); 
           }        
       }
       return md;
    }

    public String getMetadataAsString(Map<String, List<String>> md) {
        Iterator<String> keySetIterator = md.keySet().iterator(); 
        List<String> lines = new ArrayList<String>();
        while(keySetIterator.hasNext()) { 
            String key = keySetIterator.next(); 
            List<String> values = md.get(key);
            int index = 0;
            for (String value : values) {
                String letter = (char) (65+index++);
                lines.add("\newcommand{" + key + "@" + letter + "}{");
                lines.add(latex_escape(value)+"}");
            }
        }
        String block = "% LATEX BLOCK\n\makeatletter\n";
        for(String line: lines) {
            block = block + line + "\n";
        }
        block = block + "\makeatother\n% END LATEX BLOCK\n\n\n";
        return block;
    }

    private void createTempFile(String dir, Item item) throws IOException {
        Map<String, List<String>> md = getMetadataHash(item);
        String metadataStr = getMetadataAsString(md);
        FileUtils.writeStringToFile(new File(dir + "metadata.tex"), metadataStr);
    }
}
