package org.deidentifier.arx.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.linearbits.objectselector.Selector;
import de.linearbits.objectselector.SelectorBuilder;
import de.linearbits.subframe.graph.Field;
import de.linearbits.subframe.graph.Labels;
import de.linearbits.subframe.graph.Plot;
import de.linearbits.subframe.graph.PlotHistogramClustered;
import de.linearbits.subframe.graph.Series3D;
import de.linearbits.subframe.io.CSVFile;
import de.linearbits.subframe.render.GnuPlotParams;
import de.linearbits.subframe.render.GnuPlotParams.KeyPos;
import de.linearbits.subframe.render.LaTeX;
import de.linearbits.subframe.render.PlotGroup;

public class BenchmarkAnalysis {
    /** The number of builds to be included in the analysis */
    private static final int    numVersions = 5;
    /** The path of the benchmark csv files */
    private static final String path        = "build/junitReports";
    /** The filename prefix of the benchmark csv files. Used for directory filename filtering */
    private static final String filePrefix  = "benchmark_";
                                            
    /**
     * Main method
     * @param args
     * @throws IOException
     * @throws ParseException
     */
    public static void main(String[] args) throws IOException, ParseException {
        plotHistogram();
    }
    
    /**
     * Returns the Guplot parameters
     * @return
     */
    private static GnuPlotParams getCommonGnuPlotParams() {
        final GnuPlotParams params = new GnuPlotParams();
        params.keypos = KeyPos.OUTSIDE_TOP;
        params.colorize = true;
        
        params.font = "Times-Roman,6";
        
        params.rotateXTicks = -90;
        params.size = 1.5d;
        
        params.minY = 1d;
        params.logY = true;
        params.printValues = false;
        params.categorialX = true;
        return params;
    }
    
    /**
     * Returns temporary files containing the values to be analysed
     * @return
     * @throws IOException
     */
    private static ArrayList<File> getFiles() throws IOException {
        File directory = new File(path);
        
        FilenameFilter textFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String lowercaseName = name.toLowerCase();
                if (lowercaseName.startsWith(filePrefix)) {
                    return true;
                } else {
                    return false;
                }
            }
        };
        
        File[] files = directory.listFiles(textFilter);
        
        if ((files == null) || (files.length == 0)) {
            throw new IllegalArgumentException("No result files found at: " + directory.getAbsolutePath());
        }
        
        Arrays.sort(files, Collections.reverseOrder(new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                String f1Name = getPlotTitle(f1);
                String f2Name = getPlotTitle(f2);
                int compare = f1Name.compareTo(f2Name);
                
                if (compare == 0) {
                    return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
                } else {
                    return compare;
                }
                
            }
        }));
        
        String title = null;
        BufferedWriter temp = null;
        int cnt = 0;
        boolean skipHeaders = false;
        
        ArrayList<File> tempFiles = new ArrayList<File>();
        
        for (File file : files) {
            String currentTitle = getPlotTitle(file);
            
            if ((title == null) || !title.equals(currentTitle)) {
                title = currentTitle;
                if (temp != null) {
                    temp.close();
                }
                File tempFile = File.createTempFile(title + "_", ".csv");
                tempFiles.add(tempFile);
                temp = new BufferedWriter(new FileWriter(tempFile));
                cnt = 0;
                skipHeaders = false;
            }
            
            cnt++;
            
            if (cnt > numVersions) {
                continue;
            }
            
            try {
                BufferedReader in = new BufferedReader(new FileReader(file));
                String line;
                int linesRead = 0;
                while ((line = in.readLine()) != null) {
                    linesRead++;
                    if (skipHeaders && (linesRead < 3)) {
                        continue;
                    }
                    if (line.trim().length() > 0) {
                        temp.write(line);
                        temp.newLine();
                    }
                }
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            skipHeaders = true;
        }
        
        if (temp != null) {
            temp.close();
        }
        
        return tempFiles;
    }
    
    /**
     * Returns the plot for the given series
     * @param series
     * @param title
     * @return
     */
    private static PlotGroup getHistogram(Series3D series, String title) {
        Plot<?> plot = new PlotHistogramClustered("", new Labels("Testid", "Execution time"), series);
        List<Plot<?>> plotList = new ArrayList<>();
        plotList.add(plot);
        PlotGroup group = new PlotGroup(title, plotList, getCommonGnuPlotParams(), 1.0d);
        return group;
    }
    
    /**
     * Gets the plot title from the given file
     * @param file
     * @return
     */
    private static String getPlotTitle(File file) {
        String fileName = file.getName();
        int index = fileName.lastIndexOf("_");
        return fileName.substring(index + 1, fileName.length() - 4);
    }
    
    /**
     * Gets the plot title from the given temp file
     * @param file
     * @return
     */
    private static String getPlotTitleTempFile(File file) {
        String fileName = file.getName();
        int index = fileName.indexOf("_");
        return fileName.substring(0, index);
    }
    
    /**
     * Gets the series from the file
     * @param file
     * @return
     * @throws ParseException
     * @throws IOException
     */
    private static Series3D getSeries(File file) throws ParseException, IOException {
        CSVFile csvFile = new CSVFile(file);
        
        Selector<String[]> selector = null;
        SelectorBuilder<String[]> selectorbuilder = csvFile.getSelectorBuilder()
                                                           .begin()
                                                           .field("Version")
                                                           .neq("select all")
                                                           .end();
        selector = selectorbuilder.build();
        Series3D series = new Series3D(csvFile, selector, new Field("Testid"), new Field("Git commit"), new Field("Execution time", "Arithmetic Mean"));
        return series;
    }
    
    /**
     * Plots the histogram
     * @throws IOException
     * @throws ParseException
     */
    private static void plotHistogram() throws IOException, ParseException {
        
        ArrayList<File> tempfiles = getFiles();
        List<PlotGroup> plotgroup = new ArrayList<PlotGroup>();
        
        for (File file : tempfiles) {
            Series3D series = getSeries(file);
            plotgroup.add(getHistogram(series, getPlotTitleTempFile(file)));
        }
        
        LaTeX.plot(plotgroup, path + "/result", false);
        
        // delete temp files
        for (File file : tempfiles) {
            file.delete();
        }
        
    }
    
}
