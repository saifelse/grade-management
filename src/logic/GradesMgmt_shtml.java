/* Copyright (c) 2010 Richard Chan */
package logic;

import helper.GDataDownloader;
import helper.GDataDownloader.FolderCannotBeFoundException;
import helper.Helpers;
import helper.Helpers.CallbackWithException;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math.stat.descriptive.rank.Median;

import output.StaffReport;
import output.StudentReport;

import model.Constants;
import model.Grade;
import model.GradeType;
import model.Stats;
import model.Student;
import model.StudentAux;
import reader.ClasslistReader;
import reader.ClasslistReader.ClasslistRow;
import reader.GradesheetReader;
import reader.GradesheetReader.GradesheetRow;

import reader.ConfigReader;
import reader.ConfigReader.ConfigRow;

import com.google.gdata.util.ServiceException;

import exception.ModelException;
import exception.ReaderException;

public class GradesMgmt_shtml {

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
        
        boolean isDropping = true;
        boolean isSoftPsetGrade = true;
        GradesMgmt_shtml gm = new GradesMgmt_shtml(isDropping, isSoftPsetGrade, 2.0);
        try {
            gm.download();
            gm.read();
            gm.compute();
            gm.output();
            System.out.println("INFO: DONE!");
        } catch (Exception e) {
            System.out.println("CRIT: error msg = " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // states
    private final Map<String, Student> students = new HashMap<String, Student>();
    private final Set<String> allGradeKeys = new HashSet<String>();
    private final Set<String> completedGradeKeys = new HashSet<String>();
    private final Map<GradeType, Integer> numGradeTypes = new HashMap<GradeType, Integer>();
    private final Map<GradeType, Stats> gradeTypeStats = new HashMap<GradeType, Stats>();
    private Stats grandTotalStats = null;
    
    private final boolean isDropping;
    private final boolean isSoftPsetGrade;
    
    // data source related
    private GDataDownloader downloader = null;
    private List<String> filenames = null;
    
    // readers
    private ClasslistReader classlistReader = null;
    
    public GradesMgmt_shtml(boolean isDropping, boolean isSoftPsetGrade, final double gradeForExcusedCP) {
        this.isDropping = isDropping;
        this.isSoftPsetGrade = isSoftPsetGrade;
        Grade.defaultGradeValueSetter = new Helpers.CallbackWithReturn<GradeType, Double>() {
            public Double call(GradeType t) {
                if (t == GradeType.CP)
                    return gradeForExcusedCP;
                else
                    return 0.0;
            }
        };
        // initializing numGradeTypes
        for (GradeType t : GradeType.values()) {
            numGradeTypes.put(t, 0);
        }
    }
    
    
    public void download() throws FolderCannotBeFoundException, IOException, ServiceException {
        downloader = new GDataDownloader(LOGIN, PASSWORD, FOLDER_NAME);
        filenames = downloader.downloadSpreadsheets(new String[]{}); // place extra spreadsheets in {}
    }
        
    public void read() throws ReaderException {
        // Parse class list
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
        // Add students.
        for (ClasslistRow row = classlistReader.getNextRow(); row != null; row = classlistReader.getNextRow()) {
        	students.put(row.student.email, row.student);
        }
        // Remove dropped students
        for (Iterator<Student> it = students.values().iterator(); it.hasNext();) {
            Student s = it.next();
            if (s.isDropped)
                it.remove();
        }
        
        
        
        
        // Lookup max weights for keys
        Helpers.filterInPlace("CONFIG", filenames, true, new CallbackWithException<String, ReaderException>() {
            public void call(String obj) throws ReaderException {
                try {
                    ConfigReader r = new ConfigReader(new FileReader(obj));
                    processConfig(obj, r);
                } catch (FileNotFoundException e) {
                    throw new ReaderException(e.getMessage(), true);
                } catch (ReaderException e) {
                    e.printStackTrace();
                }
            }
            
        });
        
        
        
        
        // Grade sheets.
        //ORM: exception thrown when a sheet doesn't have stuff in it.
        Helpers.filterInPlace("GRADESHEET", filenames, false, new CallbackWithException<String, ReaderException>() {
            public void call(String obj) throws ReaderException {
                try {
                    GradesheetReader gradesheetReader = new GradesheetReader(obj, new FileReader(obj));
                    processGradesheet(obj, gradesheetReader, students, allGradeKeys);
                } catch (FileNotFoundException e) {
                    throw new ReaderException(e.getMessage(), true);
                }
            }
        });
        
    }
    
    public void compute() {
        
        // filter out completed keys
        completedGradeKeys.addAll(allGradeKeys);
      
        Set<String> keysToBeRemoved = new HashSet<String>();
        Map<String, HashMap<String, ArrayList<String>>> studs = new HashMap<String, HashMap<String, ArrayList<String>>>();
        for (Student s : students.values()) {
            for (Iterator<String> it = completedGradeKeys.iterator(); it.hasNext();) {
                String key = it.next();

                if (s.rawGrades.get(key) == null) {
                	if(!studs.containsKey(s.ta))
                		studs.put(s.ta, new HashMap<String, ArrayList<String>>());
                	if(!studs.get(s.ta).containsKey(s.email))
                		studs.get(s.ta).put(s.email, new ArrayList<String>());
                	studs.get(s.ta).get(s.email).add(key);
                    keysToBeRemoved.add(key);
                    //System.out.println("WARN: Student [" + s.email + "] missing grade [" + key + "].");
                }
            }
        }
        for(String ta : studs.keySet()){
        	System.out.println("-----"+ta+"-----");
        	for(String s : studs.get(ta).keySet()){
        		ArrayList<String> missing = studs.get(ta).get(s);
        		Collections.sort(missing);
        		System.out.println("WARN: Student [" + s + "] missing grades " + missing.toString() + ".");
        	}
        }
        completedGradeKeys.removeAll(keysToBeRemoved);
        
        // get number of each grade types in system
        for (String key : completedGradeKeys) {
            GradeType type = Constants.getGradeType(key);
            Integer num = numGradeTypes.get(type);
            numGradeTypes.put(type, (num == null) ? 1 : num + 1);
        }
        

        // decrement number of grade types for dropping if necessary
        

        if (isDropping) {
            Integer num = numGradeTypes.get(GradeType.CP);
//            Integer num = numGradeTypes.get(GradeType.R);
            int min = num == null ? 0 : Math.max(num - 2, 0);
            numGradeTypes.put(GradeType.CP, min);
//            numGradeTypes.put(GradeType.R, min);
            
            num = numGradeTypes.get(GradeType.PS);
            min = num == null ? 0 : Math.max(num - 1, 0);
            numGradeTypes.put(GradeType.PS, min);
            
            num = numGradeTypes.get(GradeType.MQ);
            min = num == null ? 0 : Math.max(num - 1, 0);
            numGradeTypes.put(GradeType.MQ, min);
        }

        // compute the totals for each student
        for (Student s : students.values()) {
            computeStudentGrades(s, numGradeTypes, completedGradeKeys, isDropping, isSoftPsetGrade);
        }
        
        // compute stats
        for (final GradeType type : GradeType.values()) {
            Stats s = new Stats();
            
            double[] values = new double[students.size()];
            Student[] sorted = new Student[students.size()];
            int i = 0;
            for (Student student : students.values()) {
                Double t = student.aux.typeTotals.get(type);
                sorted[i] = student;
                values[i++] = (t == null ? 0.0 : t);
            }
            s.mean = new Mean().evaluate(values);
            s.median = new Median().evaluate(values);
            s.stddev = new StandardDeviation(false).evaluate(values);
            Arrays.sort(sorted, new Comparator<Student>() {
                public int compare(Student a, Student b) {
                    Double at = a.aux.typeTotals.get(type);
                    if (at == null) at = 0.0;
                    Double bt = b.aux.typeTotals.get(type);
                    if (bt == null) bt = 0.0;
                    return (int) (at * 1000.0 - bt * 1000.0);
                }
            });
            s.sorted = sorted;
            
            gradeTypeStats.put(type, s);
        }
        
        // grand total's stats
        grandTotalStats = new Stats();
        double[] values = new double[students.size()];
        Student[] sorted = new Student[students.size()];
        int i = 0;
        for (Student student : students.values()) {
            sorted[i] = student;
            values[i++] = student.aux.grandTotal;
        }
        grandTotalStats.mean = new Mean().evaluate(values);
        grandTotalStats.median = new Median().evaluate(values);
        grandTotalStats.stddev = new StandardDeviation(false).evaluate(values);
        Arrays.sort(sorted, new Comparator<Student>() {
            public int compare(Student a, Student b) {
                return (int) (a.aux.grandTotal * 1000000.0 - b.aux.grandTotal * 1000000.0);
            }
        });
        grandTotalStats.sorted = sorted;
        for (int j = sorted.length - 1; j >= 0; j--) {
            sorted[j].aux.rank = sorted.length - j - 1;
        }
    }
    
    public void output() throws IOException {
        for (Student student : grandTotalStats.sorted) {
        	//Make the .shtml file
        	FileWriter fstreamStub = new FileWriter("output/staff/grades/" + student.fullEmail + ".shtml");
        	BufferedWriter outStub = new BufferedWriter(fstreamStub);
        	StudentReport.writeStub(student, outStub);
        	outStub.close();
        	
        	//Make the data file
            FileWriter fstreamData = new FileWriter("output/staff/grades/data/"+student.fullEmail + ".js");
            BufferedWriter outData = new BufferedWriter(fstreamData);
            StudentReport.writeData(student, isSoftPsetGrade, gradeTypeStats, completedGradeKeys, grandTotalStats, numGradeTypes, outData);
            outData.close();
            
            //Make the data file
            //FileWriter fstream = new FileWriter(student.fullEmail + ".shtml");
            //BufferedWriter out = new BufferedWriter(fstream);
            //StudentReport.write(student, isSoftPsetGrade, gradeTypeStats, completedGradeKeys, grandTotalStats, numGradeTypes, out);
            //out.close();
        }
        
        FileWriter fstream = new FileWriter("output/staff/grades/staff.shtml");
        BufferedWriter out = new BufferedWriter(fstream);
        StaffReport.write(grandTotalStats.sorted, grandTotalStats, out);
        out.close();
    }

    private static void processGradesheet(String f, GradesheetReader gradesheetReader,
            Map<String, Student> students, Set<String> allGradeKeys) throws ReaderException {
        GradesheetRow row = null;
        do {
            try {
                row = gradesheetReader.getNextRow();
                if (row != null) {
                    Student student = students.get(row.email);
                    if (student == null) {
                        System.out.println("WARN: [" + f + "]: student [" + row.email + "] unknown.");
                    } else {
                        if (!student.isDropped) {
                            for (Grade g : row.grades.values()) {
                                student.addGrade(g.key, g);
                                allGradeKeys.add(g.key);
                            }
                        }
                    }
                }
            } catch (ReaderException e) {
                if (e.severe) throw e;
                else System.out.println("WARN: [" + f + "]: " + e.getMessage());
                continue;
            } catch (ModelException e) {
                System.out.println("WARN: [" + f + "]: " + e.getMessage());
                continue;
            }
        } while(row != null);
    }
    
    public static void processConfig(String f, ConfigReader configReader) throws ReaderException{
		try { 
			ConfigRow row;
	    	while((row = configReader.getNextRow()) != null){
	    		System.out.println(row.key+" -->  "+Double.parseDouble(row.value));
	    		Constants.addGradeMax(row.key, Double.parseDouble(row.value));
	    	}
    	}catch (ReaderException e) {
            e.printStackTrace();
        }
    }

    private static void computeStudentGrades(Student student, 
            Map<GradeType, Integer> numGradeTypes,
            Set<String> completedGrades, 
            boolean isDropping, boolean isSoftPsetGrade) {
        
        StudentAux aux = new StudentAux();
        aux.actualGrades = new HashMap<String, Grade>(student.rawGrades);
        
        // remove incomplete grades
        for (Iterator<Grade> it = aux.actualGrades.values().iterator(); it.hasNext();) {
            Grade g = it.next();
            if (!completedGrades.contains(g.key)) {
                it.remove();
            }
        }
        // Compute final grade / total
        double midtermValue = 0.0;
        double midtermMax = 0.0;
        double finalGradeValue = 0.0;
        double finalGradeMax = 0.0;
        for(Iterator<Grade> it = aux.actualGrades.values().iterator(); it.hasNext();){
        	Grade g = it.next();
        	if(g.gradeType == GradeType.F){
        		finalGradeValue += g.value;
        		finalGradeMax += Constants.getFinalPartMax(g.key);
        	}
        	if(g.gradeType == GradeType.M){
        		midtermValue += g.value;
        		midtermMax += Constants.getGradeMax(g);
        	}
        }
        
        // Adjust pset grades according to final (soft pset grades)
        if (isSoftPsetGrade) {
            //Grade finalGrade = (finalGradeKey != null) ? aux.actualGrades.get(finalGradeKey) : null;
            for (Iterator<Grade> it = aux.actualGrades.values().iterator(); it.hasNext();) {
                Grade g = it.next();
                if (GradeType.PS == g.gradeType && !g.isExcused) {
                    double grade = g.value;
                    double makeUpMax = Math.min(Constants.getGradeMax(g) - grade, Constants.getGradeMax(g)/2);
                    if(Constants.getPsetNumber(g) < 6){
                    	// Midterm
                    	if(midtermValue > 0.0){
                    		double bonus = midtermValue / midtermMax * makeUpMax;
                    		grade += bonus;
                    	}
                    } else {
                    	// Final
	                    if (finalGradeValue > 0.0) { // if the final has been taken.
	                        double bonus = finalGradeValue / finalGradeMax * makeUpMax;
	                        grade += bonus;
	                    }
                    }
                    g.value = grade;
                }
            }
        }
        
        // Remove dropped grades
        if (isDropping) {
            dropLowestGradeForType(aux, GradeType.PS);
            dropLowestGradeForType(aux, GradeType.MQ);
            dropLowestGradeForType(aux, GradeType.CP);
            dropLowestGradeForType(aux, GradeType.CP);
            dropLowestGradeForType(aux, GradeType.CP);
            dropLowestGradeForType(aux, GradeType.MRQ);
            dropLowestGradeForType(aux, GradeType.MRQ);
            dropLowestGradeForType(aux, GradeType.MRQ);
        }
        
        // Replace excused grades with average grades.
        makeExcusedGradesAverage(aux, GradeType.PS);
        makeExcusedGradesAverage(aux, GradeType.MQ);
        makeExcusedGradesAverage(aux, GradeType.MRQ);
        makeExcusedGradesMax(aux, GradeType.CP);
        
        // Get max by GradeType
        for (Grade g : aux.actualGrades.values()){
        	if(g.dropped) continue;
        	//FIXME: Hack for PS to be out of correct denominator
        	double inc = g.gradeType == GradeType.PS ? Constants.getGradeTMax(GradeType.PS) : Constants.getGradeMax(g);
        	aux.typeMax.put(g.gradeType,aux.typeMax.get(g.gradeType)+inc);
        }
        
        // Get totals by GradeType
        for (Grade g : aux.actualGrades.values()) {
            // If it's dropped, ignore it.
        	if (g.dropped) continue;
        	Double t = aux.typeTotals.get(g.gradeType);            
            if (g.gradeType == GradeType.PS){
            	t = (t == null) ? g.value : (t + g.value*Constants.getGradeTMax(GradeType.PS)/Constants.getGradeMax(g));
            } else{
            	t = (t == null) ? g.value : t + g.value;
            }
            aux.typeTotals.put(g.gradeType, t);
        }
        
        // get grand total
        aux.grandTotal = 0.0;
        for (Entry<GradeType, Double> e : aux.typeTotals.entrySet()) {
            GradeType t = e.getKey();
            double max;
            if(t == GradeType.F){ // TODO: GradeType.M 
            	//max = Constants.getGradeTypeMax(t); //FIXME
            	max = aux.typeMax.get(t); // FIXME ?
            }else{
            	//max = Constants.getGradeTypeMax(t) * numGradeTypes.get(t);
            	max = aux.typeMax.get(t);
            }
            if (max > 0) {
                double delta = e.getValue() / max * Constants.getGradeTypeWeight(t);
                aux.grandTotal += delta;
            }
        }
        
        // link auxiliary data
        student.aux = aux;
    }
    
    private static void makeExcusedGradesMax(StudentAux aux, GradeType type){
    	for(Grade g : aux.actualGrades.values()){ 
    		if(g.gradeType != type) continue;
    		if(g.dropped) continue;
    		if(g.isExcused){
    			g.value = Constants.getGradeMax(g);
    		}
    	}
    }
    private static void makeExcusedGradesAverage(StudentAux aux, GradeType type){
    	
    	double tot = 0.0;
    	double max = 0.0;
    	double avg;
    	for(Grade g : aux.actualGrades.values()){ 
    		if(g.gradeType != type) continue;
    		if(g.dropped || g.isExcused) continue;
    		
    		// Determine the average
    		
    		if (g.gradeType != GradeType.PS){
	    		tot += g.value;
	    		max += Constants.getGradeMax(g);
    		} else {
    			tot += g.value/Constants.getGradeMax(g)*Constants.getGradeTMax(g.gradeType);
    			max += Constants.getGradeTMax(g.gradeType);
    		}
    	}
    	
    	avg = tot/max;
    	
    	//Set excused grades to average
    	for(Grade g : aux.actualGrades.values()){ 
    		if(g.gradeType != type) continue;
    		if(g.dropped) continue;
    		if(g.isExcused){
    			if (g.gradeType != GradeType.PS){
    				g.value = avg * Constants.getGradeMax(g);
    			} else {
    				g.value = avg * Constants.getGradeMax(g);///Constants.getGradeTMax(g.gradeType);
    			}
    		}
    	}
    	
    	/*
    	double sum = 0.0;
    	int count = 0;
    	//Compute average
    	for(Grade g : aux.actualGrades.values()){ 
    		if(g.gradeType != type) continue;
    		if(g.dropped || g.isExcused) continue;
    		
    		//Add to sum, adjust for pset.
    		if (g.gradeType == GradeType.PS){
            	sum += g.value*Constants.getGradeTMax(g.gradeType)/Constants.getGradeMax(g);
            } else{
            	sum += g.value;
            }
    		//Increment count
    		count++;
    	}
    	double avg = sum/count;
    	
    	//Set excused grades to average
    	for(Grade g : aux.actualGrades.values()){ 
    		if(g.gradeType != type) continue;
    		if(g.dropped) continue;
    		if(g.isExcused){
    			if(g.gradeType == GradeType.PS) {
    				g.value = avg*Constants.getGradeMax(g)/Constants.getGradeTMax(g.gradeType);
    			}else {
    				g.value = avg;
    			}
    		}
    	}
    	*/
    }
    
    private static void dropLowestGradeForType(StudentAux aux, GradeType type) {
    	if (type != GradeType.PS){
	        String lowest = getLowestGradeForType(aux, type);
	        if (lowest != null) {
	            aux.actualGrades.get(lowest).dropped = true;
	            // aux.actualGrades.remove(lowest);
	        }
    	} else {
	    	String lowest = getLowestPsetScore(aux);
	    	if (lowest != null) {
	    		aux.actualGrades.get(lowest).dropped = true;
	    	}
    	
    	}
    }
    
    private static String getLowestPsetScore(StudentAux aux){
    	Grade lowest = null;
    	for (Grade g : aux.actualGrades.values()){
    		if (g.gradeType == GradeType.PS && g.dropped == false && g.isExcused == false) {
    				if (lowest == null || g.value/Constants.getGradeMax(g) < lowest.value/Constants.getGradeMax(lowest)){
    					lowest = g;
    				}
    		}
    	}
    	return lowest == null ? null : lowest.key;
    }
    
    private static String getLowestGradeForType(StudentAux aux, GradeType type) {
    	assert(type != GradeType.PS);
        Grade lowest = null;
        for (Grade g : aux.actualGrades.values()) {
            if (g.gradeType == type && g.dropped == false && g.isExcused == false
            	&& !(type == GradeType.MQ && Constants.getGradeMax(g) == 5)){
            	// && !(type == GradeType.CP && Constants.getGradeMax(g) != 2.0)){
                if (lowest == null || g.value < lowest.value) {
                    lowest = g;
                }
            }
        }
        return lowest == null ? null : lowest.key;
    }
    
}
