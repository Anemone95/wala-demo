package top.anemone.walaTarget;

public class Branch {
    public static void main(String[] args) {
        String s;
        if (args[2].equals("clean")){
            s=clean(args[1]);
        } else {
            s=unclean(args[1]);
        }
        sqlselect(s);
    }
    public static String clean(String s){
        return s.replace("'","\\'");
    }
    public static String unclean(String s){
        return s;
    }

    public static void sqlselect(String cmd) {
        System.out.println(cmd);
    }
}
