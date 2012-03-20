/* Copyright (c) 2010 Richard Chan */
package model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import exception.ModelException;

public class Student {
    public final String email;
    public final String first;
    public final String last;
    public final String ta;
    public final boolean isDropped;
    public final String fullEmail;
    public final Map<String, Grade> rawGrades = new HashMap<String, Grade>();
    public StudentAux aux = null;
    
    public Student(String email, String first, String last, String ta, boolean isDropped) {
        this.email = email;
        this.fullEmail = fixEmail(email);
        this.first = first;
        this.last = last;
        this.ta = ta;
        this.isDropped = isDropped;
    }
    private String fixEmail(String e){
    	int i = e.indexOf("@");
    	String username, domain;
    	if(i == -1){
    		username = e;
    		domain = "mit.edu";
    	}else{
    		username = e.substring(0, i);
    		domain = e.substring(i+1);
    	}
    	return username.toLowerCase()+"@"+domain.toUpperCase();
    }
    /**
     * @throws ModelException if key already exists.
     */
    public void addGrade(String key, Grade grade) throws ModelException {
        if (rawGrades.containsKey(key)) {
            throw new ModelException("Grade for key " + key + email +" already exists.");
        }
   //     System.out.println("student: " + this.email + " adding grade: " + grade + " for assignment: " + key);
        rawGrades.put(key, grade);
    }
    
    public Map<String, Grade> getGrades() {
        return Collections.unmodifiableMap(rawGrades);
    }
}
