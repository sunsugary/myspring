package cn.iukme.framework.servlet;

import cn.iukme.framework.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handler记录Controoler中的RequestMapping和Method的对应关系
 */
public class Handler {
    //方法对应的实例
    public Object controller;
    //映射的方法
    public Method method;
    //URL正则匹配
    public  Pattern pattern;
    //参数顺序
    public Map<String, Integer> paramIndexMapping;

    public Handler(Object controller, Method method, Pattern pattern) {
        this.controller = controller;
        this.method = method;
        this.pattern = pattern;
        paramIndexMapping = new HashMap<String, Integer>();
        putParamIndexMapping(method);
    }

    private void putParamIndexMapping(Method method) {

        //提取方法中加了注解的参数
        Annotation[][] pa = method.getParameterAnnotations();
        for(int i = 0; i < pa.length; i ++) {
            for(Annotation a : pa[i]) {
                if(a instanceof MyRequestParam) {
                    String paramName = ((MyRequestParam) a).value();
                    if(!"".equals(paramName)) {
                        paramIndexMapping.put(paramName, i);
                    }
                }
            }
        }

        //提取方法中的request和response参数
        Class<?> [] paramTypes = method.getParameterTypes();
        for(int i = 0; i < paramTypes.length; i ++) {
            Class<?> type = paramTypes[i];

            if(type == HttpServletRequest.class || type == HttpServletResponse.class) {
                paramIndexMapping.put(type.getName(), i);
            }
        }
        this.toString();
    }

    @Override
    public String toString() {
        return "Handler{" +
                "controller=" + controller +
                ", method=" + method +
                ", pattern=" + pattern +
                ", paramIndexMapping=" + paramIndexMapping +
                '}';
    }

}
