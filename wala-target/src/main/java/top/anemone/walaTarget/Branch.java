package top.anemone.walaTarget;

public class Branch {
    public static void main(String[] args) {
        if (args[2].equals("clean")){
            clean(args[1]);
        } else {
            unclean(args[1]);
        }

    }
    public static String clean(String s){
        return s.replace("'","");
    }
    public static String unclean(String s){
        return s;
    }
}
