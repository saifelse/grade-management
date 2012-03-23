/* Copyright (c) 2010 Richard Chan */
package output;

import helper.Helpers;

import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import model.Constants;
import model.Grade;
import model.GradeType;
import model.Stats;
import model.Student;

public class StudentReport {

    // TODO: use some kind of template engine
    
    private static String TEMPLATE = null;
    private static String TEMPLATE_STAFF = null;
    
    public static void setup() throws IOException {
        if (TEMPLATE == null)
            TEMPLATE = Helpers.readFile(StudentReport.class, "/template/student_report.html");
    }
    public static void setupStaffReports() throws IOException {
        if (TEMPLATE_STAFF == null)
            TEMPLATE_STAFF = Helpers.readFile(StudentReport.class, "/template/student_report_staff_view.shtml");
    }
    
    
    public static void write(
            Student student, boolean isSoftPsetGrade,
            Map<GradeType, Stats> gradeTypeStats, Set<String> completedGradeKeys,
            Stats grandTotalStats,
            Map<GradeType, Integer> numGradeTypes, 
            Writer out) throws IOException {
        setup();
        String writeData = generateWriteData(student, isSoftPsetGrade, gradeTypeStats, completedGradeKeys, grandTotalStats, numGradeTypes);      
        out.write(TEMPLATE.replaceFirst("#CONTENT#", writeData));
    }
    public static void writeData(
            Student student, boolean isSoftPsetGrade,
            Map<GradeType, Stats> gradeTypeStats, Set<String> completedGradeKeys,
            Stats grandTotalStats,
            Map<GradeType, Integer> numGradeTypes, 
            Writer out) throws IOException {
        String writeData = generateWriteData(student, isSoftPsetGrade, gradeTypeStats, completedGradeKeys, grandTotalStats, numGradeTypes);      
        out.write(writeData);
    }
    public static void writeStub(
            Student student,
            Writer out) throws IOException {
        setupStaffReports();  
        out.write(TEMPLATE_STAFF.replaceFirst("#CONTENT#", student.fullEmail));
    }
    private static String generateWriteData(Student student, boolean isSoftPsetGrade,
            Map<GradeType, Stats> gradeTypeStats, Set<String> completedGradeKeys,
            Stats grandTotalStats,
            Map<GradeType, Integer> numGradeTypes) {
        
        StringBuffer sb = new StringBuffer();
        sb.append(Helpers.writeJSVar("name", Helpers.addSingleQuote(student.last + ", " + student.first)));
        sb.append(Helpers.writeJSVar("timestamp", 
          Helpers.addSingleQuote("Grades compiled at: " + 
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date()))));
        
        for (GradeType type : new GradeType[] { GradeType.PS, GradeType.MQ, GradeType.RC, GradeType.CP, GradeType.T, GradeType.F, GradeType.M }) {
//        for (GradeType type : new GradeType[] { GradeType.PS, GradeType.Q, GradeType.R, GradeType.F }) {
            sb.append(writeGradesForType(type, student, isSoftPsetGrade, gradeTypeStats, completedGradeKeys));
        }
        
        sb.append(writeStats(student, gradeTypeStats, grandTotalStats, numGradeTypes));
        sb.append(writeRank(student, grandTotalStats));
        return sb.toString();
    }
    private static String writeGradesForType(GradeType type, 
            Student student, boolean isSoftPsetGrade,
            Map<GradeType, Stats> gradeTypeStats, Set<String> completedGradeKeys) {
        
        List<String> cols = new ArrayList<String>();
        cols.add("{id: 'id', label: 'id', type: 'string'}");
        if (type == GradeType.PS) {
            cols.add("{id: 'pts', label: 'adjusted score', type: 'number'}");
            if (isSoftPsetGrade)
                cols.add("{id: 'unadjusted', label: 'raw score', type: 'number'}");
        } else {
            cols.add("{id: 'pts', label: 'pts', type: 'number'}");
        }
        cols.add("{id: 'max', label: 'max', type: 'number'}");
        if (type == GradeType.CP) {
//        if (type == GradeType.R) {
//            cols.add("{id: 'makeup', label: 'pending makeup', type: 'string'}");
        } else if (type == GradeType.MQ || type == GradeType.PS || type == GradeType.F || type == GradeType.M) {
//        } else if (type == GradeType.Q || type == GradeType.PS) {
            cols.add("{id: 'statistics', label: 'statistics', type: 'string'}");
        }
        
        List<String> rows = new ArrayList<String>();
        for (Entry<String, Grade> entry : student.rawGrades.entrySet()) {
            if (type != Constants.getGradeType(entry.getKey())) continue;
            
            boolean completed = completedGradeKeys.contains(entry.getKey());
            String styleExtra = completed ? "" : "background: #DDDDDD; ";
            //TODO
            String statistics = "";
            
            if (completed){
                 		if (Constants.getGradeType(entry.getKey()) == GradeType.PS){
                   		 //statistics = "<a href=\"/6042/stats/"+entry.getKey() + "stats.pdf\">link</a>"; 
                 			statistics = "<a href=\"/6.042/spring12/stats/PS.html\">link</a>"; 
                   		} else if (Constants.getGradeType(entry.getKey()) == GradeType.MQ){
                   			statistics = "<a href=\"/6.042/spring12/stats/MQ.html\">link</a>";
                   		} else if(Constants.getGradeType(entry.getKey()) == GradeType.F){
                   			statistics = "<a href=\"/6.042/spring12/stats/F.html\">link</a>";
                   		} else if(Constants.getGradeType(entry.getKey()) == GradeType.M){
                   			statistics = "<a href=\"/6.042/spring12/stats/M.html\">link</a>";
                   		}
            		}
            
            List<String> rowCols = new ArrayList<String>();
            
            String colStyle = "";
            if(entry.getValue().dropped || entry.getValue().isExcused){
            	colStyle = "color: #900";
            }
            rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                    Helpers.addSingleQuote(entry.getKey()), 
                    null, 
                    "{style: 'text-align: right; width: 1%'}")));
            
            rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                    Helpers.gradeDecimalformat.format(entry.getValue().value), 
                    Helpers.addSingleQuote(
                    		entry.getValue().isExcused ? 
                    				"EX" : Helpers.gradeDecimalformat.format(entry.getValue().value)), 
                    "{style: '" + styleExtra + "font-weight: bold; width: 100px; " + colStyle + "'}")));
            if (type == GradeType.PS && isSoftPsetGrade) {
                rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                        Helpers.gradeDecimalformat.format(entry.getValue().initialValue), 
                        Helpers.addSingleQuote(Helpers.gradeDecimalformat.format(entry.getValue().initialValue)), 
                        "{style: 'width: 100px'}")));
            }

            String format1;
            String format2;
            
            //TODO(ssh): this should be unnecessary. 
            /*
    		if(type == GradeType.PS){
    			//TODO: change PS.03 to arbitrary pset
    			format1 = Helpers.gradeDecimalformat.format(Constants.getPsetNumberMax(entry.getKey()));
                format2 = Helpers.addSingleQuote(Helpers.gradeDecimalformat.format(Constants.getPsetNumberMax(entry.getKey())));
    		}else if(type == GradeType.F){
    			format1 = Helpers.gradeDecimalformat.format(Constants.getFinalPartMax(entry.getKey()));
                format2 = Helpers.addSingleQuote(Helpers.gradeDecimalformat.format(Constants.getFinalPartMax(entry.getKey())));
    		}else {
    			format1 = Helpers.gradeDecimalformat.format(Constants.getGradeTypeMax(type));
    			format2 = Helpers.addSingleQuote(Helpers.gradeDecimalformat.format(Constants.getGradeTypeMax(type)));
    		}*/
            format1 = Helpers.gradeDecimalformat.format(Constants.getGradeMax(entry.getValue()));
            format2 = Helpers.addSingleQuote(Helpers.gradeDecimalformat.format(Constants.getGradeMax(entry.getValue())));
            
            rowCols.add(Helpers.writeJSMap(Helpers.vfp(format1, format2, "{style: 'width: 100px'}")));

            		
            //if (type == GradeType.CP) {
            //    rowCols.add(Helpers.writeJSMap(Helpers.vfp(
            //            Helpers.addSingleQuote((entry.getValue().isExcused ? "YES" : "")),
            //            null, 
            //            "{style: 'text-align: right; color: red'}")));
            //} else {
                rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                        "0", 
                        Helpers.addSingleQuote(statistics),
                        "{style: 'text-align: right'}")));
            //}
            rows.add(
              Helpers.writeJSMap(new String[] {"c"}, new String[] {
                Helpers.writeJSArray(rowCols.toArray(new String[0]))}));
        }
        
        return Helpers.writeJSVar(type.name() + "_grades", 
          Helpers.writeJSMap(Helpers.ary2Map(
            new String[] {
              "cols", Helpers.writeJSArray(cols.toArray(new String[0])),
              "rows", Helpers.writeJSArray(rows.toArray(new String[0])),
            })));
        
    }

    private static Object writeStats(Student student,
            Map<GradeType, Stats> gradeTypeStats, Stats grandTotalStats, Map<GradeType, Integer> numGradeTypes) {
        
        List<String> cols = new ArrayList<String>();
        cols.add("{id: 'id', label: 'id', type: 'string'}");
        cols.add("{id: 'pts', label: 'pts', type: 'number'}");
        cols.add("{id: 'max', label: 'max', type: 'number'}");
        cols.add("{id: 'weight', label: 'weight', type: 'number'}");
        cols.add("{id: 'mean', label: 'mean', type: 'number'}");
        cols.add("{id: 'median', label: 'median', type: 'number'}");
        cols.add("{id: 'stddev', label: 'stddev', type: 'number'}");
        
        List<String> rows = new ArrayList<String>();
        for (Entry<GradeType, Stats> e : gradeTypeStats.entrySet()) {
            List<String> rowCols = writeStatsRow(student, gradeTypeStats, numGradeTypes, grandTotalStats, e);
            rows.add(Helpers.writeJSMap(new String[] {"c"}, new String[] {
              Helpers.writeJSArray(rowCols.toArray(new String[0]))}));
        }
        
        List<String> rowCols = writeStatsRow(student, gradeTypeStats, numGradeTypes, grandTotalStats, null);
        rows.add(Helpers.writeJSMap(new String[] {"c"}, new String[] {
          Helpers.writeJSArray(rowCols.toArray(new String[0]))}));
        
        return Helpers.writeJSVar("total_grades", 
          Helpers.writeJSMap(Helpers.ary2Map(
            new String[] {
              "cols", Helpers.writeJSArray(cols.toArray(new String[0])),
              "rows", Helpers.writeJSArray(rows.toArray(new String[0])),
            })));
    }

    private static String boldStyle = "font-weight: bold; background: #DDDDFF; ";
    
    // row for a type or grand total if entry == null
    private static List<String> writeStatsRow(Student student,
            Map<GradeType, Stats> gradeTypeStats,
            Map<GradeType, Integer> numGradeTypes, Stats grandTotalStats, Entry<GradeType, Stats> entry) {
        List<String> rowCols = new ArrayList<String>();
        
        rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                String.valueOf(entry == null ? GradeType.values().length : entry.getKey().ordinal()), 
                Helpers.addSingleQuote(entry == null ? "Grand Total" : Constants.getGradeTypeName(entry.getKey())), 
                "{style: '" + (entry == null ? boldStyle : "") + "text-align: right'}")));
        double total = (entry == null ? 100.0 * student.aux.grandTotal : student.aux.typeTotals.get(entry.getKey()));
        rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                Helpers.gradeDecimalformat.format(total), 
                Helpers.addSingleQuote(Helpers.gradeDecimalformat.format(total)), 
                "{style: '" + (entry == null ? boldStyle : "") + "width: 100px'}")));
        //FIXME: this is hacky
        //double max = (entry == null ? 100.0 * getGrandTotalMax(numGradeTypes) : entry.getKey() == GradeType.F ?  Constants.getGradeTypeMax(entry.getKey()) : Constants.getGradeTypeMax(entry.getKey()) * numGradeTypes.get(entry.getKey()));
        double max;
        if(entry == null){
        	max = 100.0*getGrandTotalMax(numGradeTypes);
        }else if(entry.getKey() == GradeType.F){
        	max = student.aux.typeMax.get(entry.getKey()); // FIXME
        	//max = Constants.getGradeTypeMax(entry.getKey());
        }else{
        	max = student.aux.typeMax.get(entry.getKey());
        }
        
        rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                Helpers.gradeDecimalformat.format(max), 
                Helpers.addSingleQuote(Helpers.gradeDecimalformat.format(max)),
                "{style: '" + (entry == null ? boldStyle : "") + "width: 100px'}")));
        double weight = (entry == null ? 1.0 : Constants.getGradeTypeWeight(entry.getKey()));
        rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                Helpers.gradeDecimalformat.format(weight), 
                Helpers.addSingleQuote(Helpers.gradeDecimalformat.format(weight)),
                "{style: '" + (entry == null ? boldStyle : "") + "width: 100px'}")));
        Stats stats = (entry == null ? grandTotalStats : gradeTypeStats.get(entry.getKey()));
        rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                Helpers.gradeDecimalformat.format(stats.mean), 
                Helpers.addSingleQuote(Helpers.gradeDecimalformat.format((entry == null ? 100.0 : 1.0) * stats.mean)),
                "{style: '" + (entry == null ? boldStyle : "") + "width: 100px'}")));
        rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                Helpers.gradeDecimalformat.format(stats.median), 
                Helpers.addSingleQuote(Helpers.gradeDecimalformat.format((entry == null ? 100.0 : 1.0) * stats.median)),
                "{style: '" + (entry == null ? boldStyle : "") + "width: 100px'}")));
        rowCols.add(Helpers.writeJSMap(Helpers.vfp(
                Helpers.gradeDecimalformat.format(stats.stddev), 
                Helpers.addSingleQuote(Helpers.gradeDecimalformat.format((entry == null ? 100.0 : 1.0) * stats.stddev)),
                "{style: '" + (entry == null ? boldStyle : "") + "width: 100px'}")));
        return rowCols;
    }
    
    private static double getGrandTotalMax(Map<GradeType, Integer> numGradeTypes) {
        double max = 0.0;
        for (Entry<GradeType, Integer> e : numGradeTypes.entrySet()) {
            if (e.getValue() > 0) {
                max += Constants.getGradeTypeWeight(e.getKey());
            }
        }
        return max;
    }

    private static String writeRank(Student student, Stats grandTotalStats) {
        Map<String, String> map = new HashMap<String, String>();
        double rank = student.aux.rank;
        double num = grandTotalStats.sorted.length;
        double quadrantSize = Math.ceil(num / 4.0D);
        double quadtile = Math.floor(rank / (num / 4.0D));
        map.put("start", String.valueOf((int) Math.round(quadtile * quadrantSize + 1.0D)));
        map.put("num", String.valueOf((int) num));
        map.put("quadrantSize", String.valueOf((int) Math.round(quadrantSize)));
        
        System.out.println(student.aux.rank + ".  [quadtile: " + String.valueOf((int) Math.round(quadtile * quadrantSize + 1.0D)) + "] " + student.email + "|" + student.last+ "|" + student.first + "|" + student.aux.grandTotal);
        
        return Helpers.writeJSVar("total_grades_quadtile", Helpers.writeJSMap(map));
    }
    
}
