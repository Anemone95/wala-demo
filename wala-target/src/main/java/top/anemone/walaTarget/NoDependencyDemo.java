package top.anemone.walaTarget;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class NoDependencyDemo extends SuperClass {
    public static void main(String[] args) {
        Gson gson=new Gson();
        Map<String, String> map=new HashMap<>();
        map.put("foo","bar");
        NoDependencyDemo noDependencyDemo=new NoDependencyDemo();
        noDependencyDemo.printJson(map, gson);
    }

    public  void printJson(Object o, Gson gson) {
        String json = gson.toJson(o);
        new DemoFactory().create(gson,null);
        println(json);
    }

    @Override
    public void println(String str) {
        System.out.println(str);
    }
}
