package cn.iukme.demo;


import cn.iukme.framework.annotation.MyService;

@MyService
public class UserService {


    public String getUser(String id) {
        return id+":"+"小猪猪";
    }
}
