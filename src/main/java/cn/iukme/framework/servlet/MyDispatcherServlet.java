package cn.iukme.framework.servlet;

import cn.iukme.framework.annotation.MyAutowired;
import cn.iukme.framework.annotation.MyController;
import cn.iukme.framework.annotation.MyRequestMapping;
import cn.iukme.framework.annotation.MyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String, Object> ioc = new HashMap<String, Object>();

    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("utf-8");
        resp.setContentType("text/html;charset=utf-8");
        resp.setCharacterEncoding("utf-8");
        try {
            //请求的路劲匹配对应的方法
            doDispatch(req, resp);
        } catch (Exception e) {
            //如果匹配过程中出现异常，将异常信息打印出去
            resp.getWriter().write("500 Exception, Details:\r\n" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Handler handler = getHandler(req);

        if(handler == null){
            resp.getWriter().write("404 Not Found!");
            return;
        }

        //获取方法的参数列表
        Class<?>[] parameterTypes = handler.method.getParameterTypes();

        //存放方法参数对应的值
        Object[] paramValues = new Object[parameterTypes.length];

        //获取请求中的参数
        Map<String,String[]> params = req.getParameterMap();

        //遍历这些参数
        for (Map.Entry<String, String[]> param : params.entrySet()) {
            /**
             * param.getValue()形式为 id -> ["123"]
             * 所以需要去除大括号和引号 拿到值
             */
            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", "");

            if(!handler.paramIndexMapping.containsKey(param.getKey())) continue;

            Integer index = handler.paramIndexMapping.get(param.getKey());
            paramValues[index] = convert(parameterTypes[index], value);
        }
        //设置方法中的request和response对象
        int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
        paramValues[reqIndex] = req;

        int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
        paramValues[respIndex] = resp;

        handler.method.invoke(handler.controller, paramValues);

    }

    private Object convert(Class<?> parameterType, String value) {
        /**
         * 类型转换
         */
        if(Integer.class == parameterType){
            return Integer.valueOf(value);
        }
        return value;
    }

    private Handler getHandler(HttpServletRequest req) {
        if (handlerMapping.isEmpty()) return null;

        /**
         * 获取请求uri
         */
        String url = req.getRequestURI();

        /**
         * 获取项目启动的路径
         */
        String contextPath = req.getContextPath();

        url = url.replace(contextPath, "").replace("/+", "/");


        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if(!matcher.matches()) continue;
            return handler;
        }
        return null;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("===================初始化开始==================");
        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2.扫描所有相关联的类
        doScanner(contextConfig.getProperty("scanPackge"));

        //3.初始化所有相关联的类，并且将其保存在IOC容器里面
        doInstance();

        //4.执行依赖注入（把加了@Autowired注解的字段赋值）
        doAutowired();

        //Spring 和核心功能已经完成 IOC、DI

        //5.构造HandlerMapping，将URL和Method进行关联
        initHandlerMapping();
        System.out.println("===================初始化完成==================");
    }

    private void initHandlerMapping() {
        /**
         * 处理映射
         * 当用户在浏览器输入/user/getUserById
         * 需要进入到@MyRequestMapping("getUserById")的方法
         */
        if(ioc.isEmpty()) return;

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MyController.class)) continue;

            /**
             * 如果该类注解了@MyController
             * 该类是controller层
             * 对该类进行操作
             */
            String baseUrl = "";

            /**
             * 如果该类注解了@MyRequestMapping
             * 获取其值
             */
            if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl=requestMapping.value();
            }

            /**
             * 遍历注解了@MyController类的所有方法
             */
            Method[] methods = clazz.getMethods();

            for (Method method : methods) {

                /**
                 * 判断该方法是否注解了@MyRequestMapping
                 */
                if (!method.isAnnotationPresent(MyRequestMapping.class)) continue;;

                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);

                String methodPath = requestMapping.value();
                methodPath = (baseUrl + methodPath).replaceAll("/+", "/");
                Pattern compile = Pattern.compile(methodPath);
                handlerMapping.add(new Handler(entry.getValue(),method,compile));
                System.out.println("Mapping: " + methodPath + "," + method.getName());
            }
        }
    }

    private void doAutowired() {
        /**
         * 在spring中 使用@Autowired注解可以进行依赖注入
         * 下面完成该步骤
         */
        if(ioc.isEmpty()) return;

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            /**
             * 遍历获取所有实例化过的对象
             * 通过对象获取对应的成员变量
             */
            Field[] fields = entry.getValue().getClass().getDeclaredFields();

            /**
             * 遍历所有的成员变量
             */
            for (Field field : fields) {
                if (!field.isAnnotationPresent(MyAutowired.class)) continue;

                /**
                 * 获取标注了@MyAutowired注解的成员变量
                 */
                MyAutowired autowired = field.getAnnotation(MyAutowired.class);

                /**
                 * 获取该变量名
                 */
                String beanName = autowired.value();
                if("".equals(beanName)){
                    beanName = field.getName();
                }

                /**
                 * 如果这个字段是私有的 需要进行强制访问
                 */
                field.setAccessible(true);
                try {
                    /**
                     * 这里相当于
                     * private Dog dog = new Dog();
                     */
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String scanPackge) {
        /**
         * 在application.properties中配置需要扫描的包
         * scanPackge=cn.iukme.demo
         * 将scanPackge对应值中的.替换为/
         */
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackge.replaceAll("\\.", "/"));

        /**
         * 获取该路径下所有的文件
         */
        File dir = new File(url.getFile());
        /**
         * 遍历该路径下所有的文件
         */
        for (File file : dir.listFiles()) {
            /**
             *如果该路径下面还有文件夹 进行递归扫描
             */
            if(file.isDirectory()){
                doScanner(scanPackge+"."+file.getName());
            }else{
                String className = scanPackge+"."+ file.getName().replace(".class","");
                classNames.add(className);
                System.out.println("className:"+className);
            }

        }
    }

    private void doInstance() {
        /**
         * 在上一步已经将要需要加载的类的路径拿到并存放到了classNames中
         */
        if(classNames.isEmpty()){
            return;
        }
        for (String className : classNames) {
            try {
                //通过反射加载对应的类
                Class<?> clazz = Class.forName(className);
                /**
                 * 如果该类上有标注@Mycontroller注解 对该类进行实例化
                 */
                if(clazz.isAnnotationPresent(MyController.class)){
                    Object instance = clazz.newInstance();
                    /**
                     * 将该实例要放到ioc容器中
                     */
                    //首字母小写
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,instance);
                }else if(clazz.isAnnotationPresent(MyService.class)){
                    /**
                     * 由于在@MyService注解可以自定义类名
                     * 所以优先使用自定义类名
                     */
                    MyService service = clazz.getAnnotation(MyService.class);
                    String beanName = service.value();
                    /**
                     * 如果没有使用自定义类名
                     */
                    if("".equals(beanName.trim())){
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);

                    /**
                     * 由于在service层可以实现一些接口
                     */
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> inter : interfaces) {
                        ioc.put(inter.getName(),instance);
                    }




                }



            } catch (Exception e) {
                e.printStackTrace();
            }

        }


    }

    private String lowerFirstCase(String simpleName) {
        /**
         * 在ASCII码中将值+32位数，该字母就会转为对应的小写
         */
        char[] chars = simpleName.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);
    }

    private void doLoadConfig(String contextConfigLocation) {
        /**
         * 这一步是加载web.xml中配置的contextConfigLocation对应的文件
         * 并转化为流的形式加载到Properties中
         */
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(resourceAsStream != null){
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

}
