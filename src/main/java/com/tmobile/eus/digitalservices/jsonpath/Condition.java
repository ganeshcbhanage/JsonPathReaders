package com.tmobile.eus.digitalservices.jsonpath;

import com.jayway.jsonpath.internal.filter.LogicalOperator;
import java.util.List;
import lombok.Data;

@Data
public class Condition {

  List<Condition> subConditions;
  String inlinePredicates;
  String fieldName;
  Object fieldValue;
  FilterOperator filterOperator;
  LogicalOperator logicalOperator;
}
