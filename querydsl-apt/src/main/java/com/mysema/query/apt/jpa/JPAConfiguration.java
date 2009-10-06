/*
 * Copyright (c) 2009 Mysema Ltd.
 * All rights reserved.
 * 
 */
package com.mysema.query.apt.jpa;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import com.mysema.query.annotations.QueryType;
import com.mysema.query.apt.Configuration;
import com.mysema.query.apt.VisitorConfig;

/**
 * @author tiwe
 *
 */
public class JPAConfiguration extends Configuration {
   
    private List<Class<? extends Annotation>> annotations;
    
    public JPAConfiguration(Class<? extends Annotation> entityAnn,
            Class<? extends Annotation> superTypeAnn,
            Class<? extends Annotation> embeddableAnn,
            Class<? extends Annotation> dtoAnn,
            Class<? extends Annotation> skipAnn) throws ClassNotFoundException {
        super(entityAnn, superTypeAnn, embeddableAnn, dtoAnn, skipAnn);
        this.annotations = getAnnotations();
    }
    
    @SuppressWarnings("unchecked")
    protected List<Class<? extends Annotation>> getAnnotations() throws ClassNotFoundException{
        List<Class<? extends Annotation>> annotations = new ArrayList<Class<? extends Annotation>>();
        annotations.add(QueryType.class);
        for (String simpleName : Arrays.asList(
                "Column",
                "Embedded",
                "EmbeddedId",
                "GeneratedValue",
                "Id",
                "JoinColumn",
                "ManyToOne",
                "OneToMany",
                "PrimaryKeyJoinColumn")){
            annotations.add((Class<? extends Annotation>) Class.forName("javax.persistence."+simpleName));
        }
        return annotations;
    }
    
    @Override
    public VisitorConfig getConfig(TypeElement e, List<? extends Element> elements){
        boolean fields = false, methods = false;
        for (Element element : elements){
            if (element.getKind().equals(ElementKind.FIELD) ){
                if (!fields && hasRelevantAnnotation(element)){
                    fields = true;
                }
            }else if (element.getKind().equals(ElementKind.METHOD)){
                if (!methods && hasRelevantAnnotation(element)){
                    methods = true;
                }
            }
        }    
        if (fields && !methods){
            return VisitorConfig.FIELDS_ONLY;
        }else if (methods && !fields){
            return VisitorConfig.METHODS_ONLY;
        }else{
            return VisitorConfig.ALL;    
        }        
        
    }
    
    private boolean hasRelevantAnnotation(Element element){
        for (Class<? extends Annotation> annotation : annotations){
            if (element.getAnnotation(annotation) != null){
                return true;
            }
        }        
        return false;
    }

}
