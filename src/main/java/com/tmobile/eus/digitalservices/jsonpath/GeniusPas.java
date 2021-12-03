package com.tmobile.eus.digitalservices.jsonpath;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GeniusPas {
    Map<String, List<Rule>> ruleMap;
}
