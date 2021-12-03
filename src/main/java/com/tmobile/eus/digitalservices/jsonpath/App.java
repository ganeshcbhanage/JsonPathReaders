package com.tmobile.eus.digitalservices.jsonpath;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.internal.filter.LogicalOperator;
import com.tmobile.eus.digitalservices.payment.model.PaymentsRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean("geniusPas")
    public GeniusPas getGeniusRules(@Value("classpath:GeniusRule.json") Resource resourceFile, ObjectMapper objectMapper) throws IOException {
        return objectMapper.readValue(resourceFile.getFile(), GeniusPas.class);
    }
}

@RestController
@RequestMapping(value = "json-path")
class Controller {

    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    public static final String JSON_PATH_PREFIX = "$";
    public static final String REQUEST_HEADER_PREFIX = "$.requestHeader";
    public static final String DOT_OPERATOR = ".";

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    @Qualifier("geniusPas")
    GeniusPas geniusPas;

    @PostMapping(
            value = "/pre-assessment",
            produces = "application/json",
            consumes = "application/json")
    public PaymentsRequest preAssessment(@RequestHeader Map<String, String> requestHeader,
            @RequestBody PaymentsRequest paymentsRequest) throws JsonProcessingException, IllegalAccessException {
        return executePasRules(paymentsRequest, requestHeader, "PreAssessmentPreProcessType");
    }

    @PostMapping(
            value = "/post-assessment",
            produces = "application/json",
            consumes = "application/json")
    public PaymentsRequest postAssessment(@RequestHeader Map<String, String> requestHeader,
            @RequestBody PaymentsRequest paymentsRequest) throws JsonProcessingException, IllegalAccessException {
        return executePasRules(paymentsRequest, requestHeader, "PostAssessmentPreProcessType");
    }

    public <T> T executePasRules(T t, Map<String, String> requestHeader, String processType) {
        StopWatch stopWatch = new StopWatch(processType);
        stopWatch.start();
        T t1 = null;
        try {
            List<Rule> ruleList = geniusPas.ruleMap.get(processType);
            DocumentContext inputObjDocContext =
                JsonPath.using(
                    Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL))
                    .parse(objectMapper.writeValueAsString(t));
            for (Rule rule : ruleList) {
                log.info(
                    "Check if rule: {} satisfies input provided for processType: {}",
                    rule.getRuleName(),
                    processType);
                boolean checkIfConditionsMatch = checkIfConditionsMatch(inputObjDocContext, rule.getConditions(), requestHeader);
                if (checkIfConditionsMatch) {
                    log.info(
                        "Rule: {} matched for processType: {}, performing respective actions now",
                        rule.getRuleName(),
                        processType);
                    setActions(inputObjDocContext, rule, requestHeader);
                    if (!rule.isContinueIfRuleMatched()) {
                        break;
                    }
                }
            }

            t1 = (T) objectMapper.readValue(inputObjDocContext.jsonString(), t.getClass());
        } catch (Exception e) {
            log.error("Rule lookup failed with error", e);
        }

        stopWatch.stop();
        log.info(
            "PROCESS_TYPE={}, TIME_TAKEN_IN_MILLIS={}", processType, stopWatch.getTotalTimeMillis());
        return t1;
    }

    private boolean checkIfConditionsMatch(DocumentContext inputObjDocContext, List<Condition> conditionList, Map<String, String> requestHeader) {
        boolean isPrevCondSatisfied = false;
        LogicalOperator previousLogicalOperator = null;
        for (Condition condition : conditionList) {
            List<Condition> subConditions = condition.getSubConditions();
            boolean isCurrCondSatisfied = false;
            if(CollectionUtils.isEmpty(subConditions)) {
                String inlinePredicates = condition.getInlinePredicates();
                String fieldName = condition.getFieldName();
                Object fieldValue = condition.getFieldValue();
                FilterOperator filterOperator = condition.getFilterOperator();
                isCurrCondSatisfied = StringUtils.isNotBlank(inlinePredicates) ?
                    isPredicateConditionMatch(inputObjDocContext, inlinePredicates) :
                    isNameValueConditionMatch(
                        inputObjDocContext, fieldName, fieldValue, filterOperator, requestHeader, isCurrCondSatisfied);
            } else {
                isCurrCondSatisfied = checkIfConditionsMatch(inputObjDocContext, subConditions, requestHeader);
            }

            log.debug("Condition-> {} check-> {}", condition, isCurrCondSatisfied);
            isPrevCondSatisfied = combinePrevAndCurrCondition(isPrevCondSatisfied, previousLogicalOperator,
                isCurrCondSatisfied);
            previousLogicalOperator = condition.getLogicalOperator();
        }

        return isPrevCondSatisfied;
    }

    private boolean isPredicateConditionMatch(DocumentContext inputObjDocContext,
        String inlinePredicates) {
        boolean isCurrCondSatisfied;
        List valueInReq = null;
        try {
            valueInReq = inputObjDocContext.read(inlinePredicates, List.class);
        } catch (PathNotFoundException e) {
            log.debug("jsonPath is missing {}", e.getMessage());
        }

        isCurrCondSatisfied = !CollectionUtils.isEmpty(valueInReq);
        return isCurrCondSatisfied;
    }

    private boolean combinePrevAndCurrCondition(boolean isPrevCondSatisfied,
        LogicalOperator previousLogicalOperator, boolean isCurrCondSatisfied) {
        if (previousLogicalOperator != null) {
            switch (previousLogicalOperator) {
                case AND:
                    isPrevCondSatisfied = isPrevCondSatisfied && isCurrCondSatisfied;
                    break;
                case OR:
                    isPrevCondSatisfied = isPrevCondSatisfied || isCurrCondSatisfied;
                    break;
                default:
                    isPrevCondSatisfied = isCurrCondSatisfied;
            }
        } else {
            isPrevCondSatisfied = isCurrCondSatisfied;
        }

        return isPrevCondSatisfied;
    }

    private boolean isNameValueConditionMatch(
        DocumentContext inputObjDocContext,
        String fieldName,
        Object fieldValue,
        FilterOperator filterOperator,
        Map<String, String> requestHeader,
        boolean isCurrCondSatisfied) {
        Object valueInReq = getObjectValue(inputObjDocContext, fieldName, requestHeader);
        fieldValue = getObjectValue(inputObjDocContext, fieldValue, requestHeader);
        if (fieldValue == null
            && !Arrays.asList(
            FilterOperator.IS_NULL,
            FilterOperator.IS_NOT_NULL,
            FilterOperator.IS_TRUE,
            FilterOperator.IS_FALSE)
            .contains(filterOperator)) {
            return false;
        }

        boolean isfieldValuePresent = fieldValue != null;
        switch (filterOperator) {
            case EQUAL:
                isCurrCondSatisfied = isfieldValuePresent && fieldValue.equals(valueInReq);
                break;
            case NOT_EQUAL:
                isCurrCondSatisfied = isfieldValuePresent && !fieldValue.equals(valueInReq);
                break;
            case GREATER_THAN:
                if (valueInReq != null && fieldValue != null) {
                    isCurrCondSatisfied =
                        ObjectUtils.compare(
                            Long.valueOf(valueInReq.toString()), Long.valueOf(fieldValue.toString()))
                            > 0;
                }
                break;
            case IN:
                isCurrCondSatisfied =
                    valueInReq != null
                        && isfieldValuePresent
                        && fieldValue.toString().contains(valueInReq.toString());
                break;
            case NOT_IN:
                isCurrCondSatisfied =
                    (valueInReq == null)
                        || (isfieldValuePresent && !fieldValue.toString().contains(valueInReq.toString()));
                break;
            case IS_NULL:
                isCurrCondSatisfied = valueInReq == null;
                break;
            case IS_NOT_NULL:
                isCurrCondSatisfied = valueInReq != null;
                break;
            case IS_TRUE:
                isCurrCondSatisfied = valueInReq != null && BooleanUtils.isTrue(isValueInReq(valueInReq));
                break;
            case IS_FALSE:
                isCurrCondSatisfied = valueInReq == null || BooleanUtils.isFalse(isValueInReq(valueInReq));
                break;
            default:
                isCurrCondSatisfied = isfieldValuePresent && fieldValue.equals(valueInReq);
        }

        return isCurrCondSatisfied;
    }

    private boolean isValueInReq(Object valueInReq) {
        boolean isValueInReq = false;
        if (valueInReq != null && valueInReq instanceof String) {
            isValueInReq = Boolean.valueOf((String) valueInReq);
        }

        if (valueInReq != null && valueInReq instanceof Boolean) {
            isValueInReq = (boolean) valueInReq;
        }
        return isValueInReq;
    }

    private Object getObjectValue(DocumentContext inputObjDocContext, Object fieldName, Map<String, String> requestHeader) {
        Object valueInReq = fieldName;
        if(fieldName!=null && fieldName.toString().startsWith("$")) {
            String fieldNameKey = fieldName.toString();
            if(StringUtils.startsWith(fieldNameKey, "$.requestHeader")) {
                String[] split = StringUtils.split(fieldNameKey, ".");
                String headerKey = split[2];
                return requestHeader.get(headerKey);
            }

            try {
                valueInReq = inputObjDocContext.read(fieldNameKey);
            } catch (PathNotFoundException e) {
                log.debug("jsonPath is missing {}", e.getMessage());
            }
        }

        return valueInReq;
    }

    private void setActions(
        DocumentContext inputObjDocContext, Rule rule, Map<String, String> requestHeader) {
        int currArrayIndex = 0;
        int prevArrayIndex = 0;
        for (Action action : rule.getActions()) {
            String fieldName = action.getFieldName();
            Object fieldValue = getObjectValue(inputObjDocContext, action.getFieldValue(), requestHeader);
            StringBuilder rootFieldName = new StringBuilder(JSON_PATH_PREFIX);
            String[] fieldNameTokens = StringUtils.split(fieldName, DOT_OPERATOR);
            boolean isArrayEmpty = false;
            for (String fieldNameToken : fieldNameTokens) {
                if (JSON_PATH_PREFIX.equals(fieldNameToken)) {
                    continue;
                }

                if ((rootFieldName.toString() + DOT_OPERATOR + fieldNameToken).equals(fieldName)) {
                    if (isArrayEmpty || currArrayIndex > prevArrayIndex) {
                        Map linkedMap = new LinkedHashMap();
                        linkedMap.put(fieldNameToken, fieldValue);
                        inputObjDocContext.add(
                            rootFieldName.toString().substring(0, rootFieldName.toString().lastIndexOf('[')),
                            linkedMap);
                        prevArrayIndex = currArrayIndex;
                    } else {
                        inputObjDocContext.set(fieldName, fieldValue);
                    }

                    break;
                }

                if (StringUtils.contains(fieldNameToken, "[")) {
                    rootFieldName = rootFieldName.append(DOT_OPERATOR + fieldNameToken);
                    List listRead =
                        inputObjDocContext.read(
                            rootFieldName.toString().substring(0, rootFieldName.toString().lastIndexOf('[')),
                            List.class);
                    currArrayIndex = Integer.parseInt(StringUtils.substringBetween(fieldNameToken, "[", "]"));
                    if (listRead == null) {
                        inputObjDocContext.set(
                            rootFieldName.toString().substring(0, rootFieldName.lastIndexOf("[")),
                            new JSONArray());
                        isArrayEmpty = true;
                        prevArrayIndex = 0;
                    }

                    if (CollectionUtils.isEmpty(listRead)) {
                        isArrayEmpty = true;
                        prevArrayIndex = 0;
                    }

                } else {
                    Object tokenRead =
                        inputObjDocContext.read(rootFieldName.toString() + DOT_OPERATOR + fieldNameToken);
                    if (tokenRead == null) {
                        inputObjDocContext.put(rootFieldName.toString(), fieldNameToken, new LinkedHashMap<>());
                    }

                    rootFieldName = rootFieldName.append(DOT_OPERATOR + fieldNameToken);
                }
            }
        }
    }
}
