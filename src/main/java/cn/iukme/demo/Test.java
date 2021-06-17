package cn.iukme.demo;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Test {

    public static void main(String[] args) {
        UserController controller = new UserController();
        Field[] fields = controller.getClass().getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            String beanName = field.getType().getName();
            System.out.println(beanName);
        }
    }
}
