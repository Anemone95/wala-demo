package top.anemone.walaTarget;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class NoDependencyDemo {
    public static void main(String[] args) {
        Gson gson=new Gson();
        Map<String, String> map=new HashMap<>();
        map.put("foo","bar");
        printJson(map, gson);
    }

    public static void printJson(Object o, Gson gson) {
        String json = gson.toJson(o);
        new DemoFactory().create(gson,null);
        System.out.println(json);
    }
}
