/* Copyright (c) 2010 Richard Chan */
package model;

import java.util.HashMap;
import java.util.Map;

public class Constants {
	private static Map<String, Double> maxValue = new HashMap<String, Double>();
	
	public static void addGradeMax(String key, Double value){
		maxValue.put(key, value);
	}
	public static Double getGradeTMax(GradeType t){
		return maxValue.get(t.toString());
	}
	public static Double getGradeMax(Grade g){
		if(g.gradeType == GradeType.F){
			return getFinalPartMax(g.key);
		}
		
		if(maxValue.containsKey(g.key)){
			return maxValue.get(g.key);
		}else{
			return getGradeTMax(g.gradeType);
		}
	}

	/*
	public static Double getCPGradeTypeMaxs(Grade g){
		if(g.key.length() > 6 && g.key.substring(7).startsWith("NQ")){
			return 0.5;
		}else {
			return 2.0;
		}
	}
    public static Double getGradeTypeMax(GradeType t) {
        switch (t) {
        case CP:
            return 2.0;
        case F:
            return 0.0;
        case MQ:
            return 10.0;
        case PS: //see the pset function for pset by pset specific.
            return 50.0;
        case RC:
            return 3.0;
        case M:
        	return 100.0;
        case T:
            return 1.0;
        }
        throw new RuntimeException("invalid grade type");
    }
    */
    
    public static Double getFinalPartMax(String finalKey){	
    	Integer partNum = Integer.parseInt(finalKey.substring(2));   	
    	double[] finalWeights = new double[]{8,7,6,8,7,8,7,8,8,11,6,8,4,4};
    	try {
	    	return finalWeights[partNum-1];
    	}catch(Exception E){
    		throw new RuntimeException("no weight defined for (update Constants.java to change it) " + finalKey);
    	}
    }
    public static int getPsetNumber(Grade pset){	
    	return Integer.parseInt(pset.key.substring(3)); 
    }
    /*
    public static Double getMQNumberWeight(String mqkey){	
    	Integer mqnum = Integer.parseInt(mqkey.substring(3)); 
    	switch (mqnum){
    	case 1: //mq #1
    		return 5.0;
    	default:
    		return 10.0;
    	}
    }
    */
    /*
    public static Double getPsetNumberMax(String psetkey){	
    	Integer psetnum = Integer.parseInt(psetkey.substring(3)); 
    	switch (psetnum){
    	//you can add cases and change the numbers to correctly deal with different total scores
    	//across psets
    	case 1: //pset #1
    		return 40.0;
    	case 2: //pset #2
    		return 40.0;
    	}
    	throw new RuntimeException("no pset weight defined for (update Constants.java to change it) " + psetkey);
    }

	*/
    public static Double getGradeTypeWeight(GradeType t) {
        switch (t) {
        /*
         * Problem Sets: 	25% 
         * Final: 	        25% 
         * Class participation	20% 
         * Miniquizzes: 	10%
         * Mid term exam:   10%
         * Weekly Reading Comments: 	5%
         * Online Tutor Problems: 	5%
         */
        case PS:
        	return 0.25;
        case F:
        	return 0.25;
        case CP:
            return 0.20;
        case MQ:
            return 0.10;
        case MRQ:
        	return 0.025;
        case M:
        	return 0.10;
        case RC:
            return 0.025;
        case T:
            return 0.05;
        }
        throw new RuntimeException("invalid grade type");
    }
    
    public static String getGradeTypeName(GradeType t) {
        switch (t) {
        case CP:
            return "Class participation";
        case F:
            return "Final Exam";
        case MQ:
            return "Miniquiz";
        case MRQ:
        	return "Microquiz";
        case M:
        	return "Midterm Exam";
        case PS:
            return "Problem Set";
        case RC:
            return "Reading Comments";
        case T:
            return "Tutorial";
        }
        throw new RuntimeException("invalid grade type");
    }

    public static GradeType getGradeType(String key) {
        for (GradeType t : GradeType.values()) {
            if (key.startsWith(t.name()+".")) {
                return t;
            }
        }
        return null;
    }

}
