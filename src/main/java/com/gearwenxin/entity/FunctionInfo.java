package com.gearwenxin.entity;

import java.util.List;

/**
 * @author Ge Mingjia
 * @date 2023/10/15
 */
public class FunctionInfo {

  /**
   * 函数名
   */
  private String name;
  
  /**
   * 函数描述
   */
  private String description;

  /**
   * 函数请求参数,说明:
   * (1)JSON Schema 格式,参考JSON Schema描述
   * (2)如果函数没有请求参数,parameters值格式如下:
   * {"type": "object","properties": {}}
   */
  private Object parameters;

  /**
   * 函数响应参数,JSON Schema 格式,参考JSON Schema描述
   */
  private Object responses;
  
  /**
   * function调用的一些历史示例
   */
  private List<Example> examples;

}