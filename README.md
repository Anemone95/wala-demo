# 安装依赖
* Graphviz
    https://graphviz.gitlab.io/

# 程序切片

假设有目标程序：

```java
package top.anemone.walatarget;

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
    public static void sink(String cmd) { //caller method
        System.out.println(cmd); //callee method
    }

    public static String replace(String str){
        return str.replace("a","b").replace("c","d");
    }
}
```

想要切与第15行相关的语句

## 0x01 构造调用图

```java
// build the call graph
com.ibm.wala.ipa.callgraph.CallGraphBuilder cgb = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope, null, null);
CallGraph cg = cgb.makeCallGraph(options, null);
PointerAnalysis pa = cgb.getPointerAnalysis();
```

## 0x02 搜索caller

WALA是通过caller+callee来定位要切片的语句的，所以先搜索caller函数，在样例中即`sink(String cmd)`：

```java
String[] callerMethod=caller.split("#");
String clazz=callerMethod[0].replace('.','/');
Atom method=Atom.findOrCreateUnicodeAtom(callerMethod[1]);
CGNode callerNode=null;
for(CGNode n: cg){
    if (n.getMethod().getReference().getDeclaringClass().getName().toString().endsWith(clazz) && n.getMethod().getName().equals(method)) {
        callerNode=n;
        break;
    }
}
if(callerNode==null){
    Assertions.UNREACHABLE("failed to find method");
}
```

## 0x03 搜索callee

接着搜索callee函数，在样例中即第15行`System.out.println(cmd);`

```java
Statement calleeStmt=null;
IR callerIR = callerNode.getIR();
for (SSAInstruction s: Iterator2Iterable.make(callerIR.iterateAllInstructions())){
    if (s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction) {
        com.ibm.wala.ssa.SSAAbstractInvokeInstruction call = (com.ibm.wala.ssa.SSAAbstractInvokeInstruction) s;
        if (call.getCallSite().getDeclaredTarget().getName().toString().equals(callee)) {
            com.ibm.wala.util.intset.IntSet indices = callerIR.getCallInstructionIndices(call.getCallSite());
            com.ibm.wala.util.debug.Assertions.productionAssertion(indices.size() == 1, "expected 1 but got " + indices.size());
            calleeStmt=new com.ibm.wala.ipa.slicer.NormalStatement(callerNode, indices.intIterator().next());
        }
    }
}
if(calleeStmt==null){
    Assertions.UNREACHABLE("failed to find call to " + callee + " in " + callerNode);
}
```

## 0x04 切片并打印结果

```java
Collection<Statement> slice = Slicer.computeBackwardSlice(calleeStmt, cg, pa, dOptions, cOptions);
for (Statement s : slice) {
    System.out.println(s);
}
```

## 分析切片结果

切片返回Statement，其为一个抽象类，有各种实现，其本身有node和kind属性

![Statement](README/Statement.png)

