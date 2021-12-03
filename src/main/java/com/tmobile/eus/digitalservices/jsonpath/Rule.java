package com.tmobile.eus.digitalservices.jsonpath;

import java.util.List;
import lombok.Data;

@Data
public class Rule {
  String ruleName;
  List<Condition> conditions;
  List<Action> actions;
  boolean continueIfRuleMatched;
}
