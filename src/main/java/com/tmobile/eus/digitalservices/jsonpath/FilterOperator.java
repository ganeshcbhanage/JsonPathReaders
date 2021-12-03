package com.tmobile.eus.digitalservices.jsonpath;

public enum FilterOperator {
  EQUAL("eq"),
  IN("in"),
  GREATER_THAN("gt"),
  LESS_THAN("lt"),
  NOT_EQUAL("!eq"),
  NOT_IN("!in"),
  IS_NULL("eq null"),
  IS_NOT_NULL("!eq null"),
  IS_TRUE("true"),
  IS_FALSE("false");

  private String value;

  FilterOperator(String value) {
    this.value = value;
  }

  public String toValue() {
    return this.value;
  }

  public static FilterOperator fromValue(String text) {
    FilterOperator[] var1 = values();
    int var2 = var1.length;

    for (int var3 = 0; var3 < var2; ++var3) {
      FilterOperator b = var1[var3];
      if (String.valueOf(b.value).equalsIgnoreCase(text)) {
        return b;
      }
    }

    throw new IllegalArgumentException(text);
  }
}
