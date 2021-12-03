package com.tmobile.eus.digitalservices.jsonpath;

import com.jayway.jsonpath.internal.filter.LogicalOperator;
import lombok.Data;

@Data
public class Action {
  String fieldName;
  Object fieldValue;
  LogicalOperator logicalOperator;
}
