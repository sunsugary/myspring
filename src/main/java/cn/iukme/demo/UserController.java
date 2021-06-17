package cn.iukme.demo;


import cn.iukme.framework.annotation.MyAutowired;
import cn.iukme.framework.annotation.MyController;
import cn.iukme.framework.annotation.MyRequestMapping;
import cn.iukme.framework.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("/user")
public class UserController {

    @MyAutowired()
    private UserService userService;

    @MyRequestMapping("/getUserById")
    public void getUserById(HttpServletRequest request, HttpServletResponse response, @MyRequestParam("id") String id){

        System.out.println("id:"+id);
        String result = userService.getUser(id);

        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
