/* Copyright (c) 2010 Richard Chan */
package logic;

import helper.GDataDownloader;
import helper.Helpers;
import helper.GDataDownloader.FolderCannotBeFoundException;
import helper.Helpers.CallbackWithException;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import output.ProblemStatsReport;

import model.Student;
import reader.ClasslistReader;
import reader.ClasslistReader.ClasslistRow;
import reader.ProblemGradesReader;
import reader.ProblemGradesReader.ProblemGradesheetRow;

import com.google.gdata.util.ServiceException;

import exception.ReaderException;

public class ProblemsStatsMgmt3 {
    
    // hack to make it easier to set params.. fix later?
    private static String LOGIN;
    private static String PASSWORD;
    private static String FOLDER_NAME;
    
    public static void main(String[] args) {
        if (args.length == 3) {
            LOGIN = args[0];
            PASSWORD = args[1];
            FOLDER_NAME = args[2];
        } else {
            System.err.println("Require arguments: [login] [password] [folder_name]");
            return;
        }
        
        ProblemsStatsMgmt3 psm = new ProblemsStatsMgmt3();
        try {
            psm.download();
            psm.read();
            System.out.println("going into output");
            psm.output();
            System.out.println("INFO: DONE!");
        } catch (Exception e) {
            System.out.println("CRIT: error msg = " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private final Map<String, Student> students = new HashMap<String, Student>();
    private final Map<String, Map<String, List<Double>>> gradeSets = new HashMap<String, Map<String, List<Double>>>();
    private final Map<String, Double> finalTotals = new HashMap<String, Double>();
    
    // data source related
    private GDataDownloader downloader = null;
    private List<String> filenames = null;
    
    // readers
    private ClasslistReader classlistReader = null;
    
    
    public void download() throws FolderCannotBeFoundException, IOException, ServiceException {
        downloader = new GDataDownloader(LOGIN, PASSWORD, FOLDER_NAME);
        filenames = downloader.downloadSpreadsheets(new String[]{}); // place extra spreadsheets in {}
    }
    

    public void read() throws ReaderException {
    	
    	// class list
        Helpers.filterInPlace("CLASSLIST", filenames, true, new CallbackWithException<String, ReaderException>() {
        	
            public void call(String obj) throws ReaderException {
                ClasslistReader r = null;
                try {
                    r = new ClasslistReader(new FileReader(obj));
                } catch (FileNotFoundException e) {
                    throw new ReaderException(e.getMessage(), true);
                } catch (ReaderException e) {
                    e.printStackTrace();
                }
                if (r != null) classlistReader = r;
            }
            
        });
        if (classlistReader == null) {
            throw new RuntimeException("Classlist cannot be found.");
        }
        for (ClasslistRow row = classlistReader.getNextRow(); row != null; row = classlistReader.getNextRow()) {
            students.put(row.student.email, row.student);
        }

        // remove dropped students
        for (Iterator<Student> it = students.values().iterator(); it.hasNext();) {
            Student s = it.next();
            if (s.isDropped)
                it.remove();
        }
        
        Helpers.filterInPlace("PSET-GRADESHEET", filenames, false, new CallbackWithException<String, ReaderException>() {
            public void call(String f) throws ReaderException {
                try {
                	ProblemGradesReader problemGradesheetReader = new ProblemGradesReader(new FileReader(f));
                    processProblemGradesheet(f, problemGradesheetReader, students);
                } catch (FileNotFoundException e) {
                    throw new ReaderException(e.getMessage(), true);
                } catch (ReaderException e){
                	System.out.println("warning: sheet with no META detected. Showing trace:");
                	e.printStackTrace();
                }
            }
        });
        Helpers.filterInPlace("MINIQUIZ-GRADESHEET", filenames, false, new CallbackWithException<String, ReaderException>() {
            public void call(String f) throws ReaderException {
                try {
                	ProblemGradesReader problemGradesheetReader = new ProblemGradesReader(new FileReader(f));
                    processProblemGradesheet(f, problemGradesheetReader, students);
                } catch (FileNotFoundException e) {
                    throw new ReaderException(e.getMessage(), true);
                } catch (ReaderException e){
                	System.out.println("warning: sheet with no META detected. Showing trace:");
                	e.printStackTrace();
                }
            }
        });
        Helpers.filterInPlace("FINAL-EXAM-GRADESHEET", filenames, false, new CallbackWithException<String, ReaderException>() {
            public void call(String f) throws ReaderException {
                try {
                	ProblemGradesReader problemGradesheetReader = new ProblemGradesReader(new FileReader(f));
                    processProblemGradesheet(f, problemGradesheetReader, students);
                    
                } catch (FileNotFoundException e) {
                    throw new ReaderException(e.getMessage(), true);
                } catch (ReaderException e){
                	System.out.println("warning: sheet with no META detected. Showing trace:");
                	e.printStackTrace();
                }
            }
        });
        // TODO : Similar for Midterm?
        //Add finals total.
        for(Entry<String, Double> e : finalTotals.entrySet()){
        	List<Double> keyGrades = getOrInitGrades("F", "F.TOT");
        	keyGrades.add(e.getValue());
        }
    }
    
    private void output() throws IOException {
        for (Entry<String, Map<String, List<Double>>> e : gradeSets.entrySet()) {
        	System.out.println("Making "+e.getKey()+".html");
            FileWriter fstream = new FileWriter(e.getKey() + ".html");
            BufferedWriter out = new BufferedWriter(fstream);
            ProblemStatsReport.write(e.getKey(), e.getValue(), out);
            out.close();
        }
    }
    private void processProblemGradesheet(String f,
            ProblemGradesReader problemGradesheetReader,
            Map<String, Student> students2) throws ReaderException {
        ProblemGradesheetRow row = null;
        do {
            try {
                row = problemGradesheetReader.getNextRow();
                if (row != null) {
                    Student student = students.get(row.email);
                    if (student == null || student.isDropped) {
                        // ignore for dropped student
                    } else {
                        for (Entry<String, Double> e : row.grades.entrySet()) {
                            String key = getSetKey(e.getKey());
                            if(key.equals("F")){
                            	if(!finalTotals.containsKey(row.email)) finalTotals.put(row.email, 0.0);
                            	finalTotals.put(row.email, finalTotals.get(row.email)+e.getValue());
                            }
                            List<Double> keyGrades = getOrInitGrades(key, e.getKey());
                            keyGrades.add(e.getValue());
                        }
                    }
                }
            } catch (ReaderException e) {
                if (e.severe) throw e;
                else System.out.println("WARN: [" + f + "]: " + e.getMessage());
                continue;
            }
            
        } while(row != null);
        
    }
    
    private static String getSetKey(String key) {
    	return key.substring(0, key.indexOf("."));
    }
    
    private List<Double> getOrInitGrades(String key, String problemKey) {
        Map<String, List<Double>> m = gradeSets.get(key);
        if (m == null) {
            m = new HashMap<String, List<Double>>();
            gradeSets.put(key, m);
        }
        List<Double> l = m.get(problemKey);
        if (l == null) {
            l = new ArrayList<Double>();
            m.put(problemKey, l);
        }
        return l;
    }
}
