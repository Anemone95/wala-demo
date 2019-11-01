package top.anemone.walaTarget;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        String s=source();
        s=replace(s);
        sink(s);
    }
    public static String source(){
        return "calc";
    }
    public static void sink(String cmd) {
        System.out.println(cmd);
        cmd=cmd+1;
        System.out.println(cmd);
    }

    public static String replace(String str){
        return str.replace("a","b").replace("c","d");
    }
}
