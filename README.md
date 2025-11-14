# 编译器设计文档

## 参考编译器介绍

[ 编译器示例代码-sysy-compiler](https://judge.buaa.edu.cn/downloadFile?filename=rA93afVaAIXqmACZMFVyM_8zM-ofn7gosegOopUvg1i7E_YmkximGBamkjFOB2B2gTRh6qDhRbpiptyJrPNkGAOljwapkHdnGchT2UajDxO13uttyBsrVrpgtTTSC4zLPfkSmrKIDhc&encname=Eg1N5MasBFec9WC65S3JntDIvUrytCbcS5-hFc9PKZbW-FoT5jZnjg)

### 总体结构

整体分为前端（词法、语法、AST）、中端（中间代码表示 IR、符号表）、后端（目标代码生成三部分，另外把错误处理与工具模块拆分出来，主入口为 `Compiler.java`。

### 接口设计

#### 前端

```java
public class FrontEnd {
    private static Lexer lexer;
    private static Parser parser;

    public static void SetInput() throws IOException {
        lexer = new Lexer(IOhandler.GetInput());
        parser = new Parser();
    }

    //词法分析器生成Token流
    public static void GenerateTokenList() throws IOException {
        lexer.GenerateTokenList();
    }
    
	//语法分析器生成语法树
    public static void GenerateAstTree() {
        parser.SetTokenStream(GetTokenStream());
        parser.GenerateAstTree();
    }

    //取Tokens
    public static ArrayList<Token> GetTokenList() {
        return lexer.GetTokenList();
    }

    //获得Token流
    private static TokenStream GetTokenStream() {
        return new TokenStream(lexer.GetTokenList());
    }

    //取语法树
    public static CompUnit GetAstTree() {
        return parser.GetAstTree();
    }
}

```

#### 中端

```java
public class MidEnd {
    private static CompUnit rootNode;
    private static IrModule irModule;

    //创建符号表
    public static void GenerateSymbolTable() {
        SymbolManger.Init();
        rootNode = FrontEnd.GetAstTree();
        rootNode.Visit();
        SymbolManger.GoBackToRootSymbolTable();
    }

    //创建中间代码
    public static void GenerateIr() {
        irModule = new IrModule();
        IrBuilder.SetCurrentModule(irModule);
        Visitor visitor = new Visitor(rootNode);
        visitor.Visit();
        IrBuilder.Check();
    }

    //获得符号表
    public static SymbolTable GetSymbolTable() {
        return SymbolManger.GetSymbolTable();
    }

    //获得中间表示
    public static IrModule GetIrModule() {
        return irModule;
    }
}

```

#### 后端

```java
public class BackEnd {
    private static IrModule midEndModule;
    private static MipsModule backEndModule;

    //生成Mips代码
    public static void GenerateMips() {
        backEndModule = new MipsModule();
        MipsBuilder.SetBackEndModule(backEndModule);

        midEndModule = MidEnd.GetIrModule();
        midEndModule.toMips();
        // 进行窥孔优化
        if (Setting.FINE_TUNING) {
            PeepHole peepHole = new PeepHole();
            peepHole.Peep();
        }
    }

    //获得Mips表示
    public static MipsModule GetMipsModule() {
        return backEndModule;
    }
}

```



### 详细分析

#### 前端

- `lexer`：词法分析，负责生成`token`流。
- `parser`：语法分析将`tokens`转为语法树。
- `ast`：完整的抽象语法树节点（`CompUnit`、`FuncDef`、`Stmt`、`Exp`、`Decl` 等），对应`parser`所使用的类。
- `FrontEnd.java`：前端的统一接口，输出`AST`、`Tokens`等供后续阶段使用。

#### 中端

- `symbol`
  - `SymbolTable` / `SymbolManager` / 各类` Symbol`：追踪作用域、类型与符号信息，建立符号表


- `visit`

  - 实现对` AST` 的遍历与语义分析、类型检查、符号绑定等。

- `midend`
  - `MidEnd.java`：中端统一接口，负责把 `AST` 转换为中间表示（`IR`），并调用` IR`层优化。
  - `llvm` 包：`IrBuilder`、`IrModule`、`IrNode`、等，表示`LLVM` 风格的中间表示与构建器。

- `optimize`
  - 多个优化和分析通道（`ActiveAnalysis`、`Lvn`、`MemToReg`、`InsertPhi`、`RemoveDeadCode/Block`、`RegisterAllocator `等），可组合用于中端 IR 优化与寄存器分配。

#### 后端

- `backend`
  - `BackEnd.java`、`PeepHole.java`：后端调度与局部 `peephole `优化。
  - `mips`：目标为 MIPS 的代码生成器（`MipsBuilder`、`MipsModule`、`Register`）以及细分的指令/汇编构造（`assembly` 下的多个类），负责把` IR `翻译为 `MIPS `汇编。

- `error`（错误处理）与 `utils`（工具）
  - `ErrorRecorder/Error/ErrorType`：集中管理编译过程中的错误与诊断信息。
  - `utils `提供 `IO`、调试、配置、复杂度处理等功能。

## 自己编译器设计

大致分为前、中、后三部分，按照词法分析、语法分析、语义分析、中间代码生成、目标代码生成、代码优化的顺序来写。

词法分析器负责生成`Token`流，语法分析器负责生成语法树。语义分析器解析生成的语法树，此部分参考上述编译器设计`Visitor`类分别对各语法成分进行分析。中间代码生成可能需要再设计另外的`Visitor`类结合`SymbolTable`来进一步分析。

目标代码生成器根据中间代码来生成，之后考虑代码优化部分。

### 文件组织(暂定)

```
├─frontend
│  └─Parser
│      ├─Decl
│      ├─Exp
│      ├─FuncDef
│      ├─MainFuncDef
│      ├─Stmt
│      ├─Token
│      └─Tree
└─midend
    ├─Symbol
    └─Visit
        ├─Decl
        ├─Exp
        ├─Func
        ├─MainFuncDef
        └─Stmt
```

### 词法分析设计

1. `token`类设计包含
   1. `token`枚举类型如`IDENFR`、`INTCON`、`CONSTTK`等；`TokenType type`
   2. 当前`token`对应字符串表示；`String lexeme`
   3. 当前`token`所在行号；
   4. ~~是否已经被输出；boolean isPrinted~~
2. 设计`Lexer`类包含
   - 源码文件流  `FileInputStream file`
   - 生成的tokens  `ArrayList<Token> tokens`
   - 产生的错误  `ArrayList<Error> errors`
   - 当前读到的字符`currentChar`，当前读到的行数`currentLine`
3. 构造`Lexer`类时初始化源码文件流，并读取第一个字符。
4. 设计分析函数，可由`Complier`调用。
5. 分析函数通过调用`getToken()`函数每处理一个`token`就将其加入`tokens`数组中。
6. `getToken()`函数声明一个`StringBuilder lexeme`，根据自动机判断当前字符应该什么归于什么类型的处理函数。
   1. 首字符为英文字符或下划线  `_`   ，进入处理标识符或关键字函数；
   2. 首字符为数字，进入整数常量处理函数；
   3. 首字符为引号  `"`   ，进入字符串常量处理函数；
   4. 首字符为  `/`   ，进入注释或除号处理函数；
   5. 首字符为  `+`   `-`  `*`  `%`  `;`  `,`  `(`  `)`  `[`  `]`  `{`  `}`    ，进入单字符运算符或分隔符处理函数；
   6. 首字符为  `=`   `>`  `<`  `!`    ，进入双字符运算符处理函数；
   7. 首字符为  `&`  ，进入与符号处理函数；
   8. 首字符为  `|`  ，进入或符号处理函数；
   9. `EOF`返回`null`；
7. 处理函数返回当前`token`。
8. 处理函数中出现的错误加入`errors`数组中，方便后续处理。

### 语法分析

1. 设计语法树：
   1. 一个语法成分对应一个结点，他们有公共的父类`Node`；
      - `Node`类包含枚举类`GrammarType type`表示当前语法成分的类型，如`Exp、IntConst`等；
      - `Node`类包含`Token token`，表示当前**终结符**对应的`Token`；
      - `Node`类包含`ArrayList<Token> tokens`，表示`Lexer`分析得到的`token`流；
      - `Node`类包含`int tokenIndex`，表示当前读入`token`流索引，进入/结束某一成分分析时`tokens[tokenIndex]`都应处于未处理状态；
      - `Node`类包含`Node parent`和`ArrayList<Node> children`分别表示父节点和子节点；
      - `Node`类包含`final String filename`和`final String ErrorFilename`分别表示正确输出文件和错误输出文件；
      - `Node`类含方法`Token peekToken(int offset)`，用于获取相对于索引`tokenIndex`偏移`offset`的值，可以预读或回读；
      - `Node`类包含方法`void printToError(Error error)`，输出对应错误时将其添加到`GlobalError`中；
   2. `Node`子类构造函数需`GrammarType type、int tokenIndex、ArrayList<Token> tokens`，终结符需设置`Token token`；
   3. 子类含分析方法，按照文法进行依次分析，调用对应成分分析方法前需：
      1. 创建对象；
      2. 添加为当前成分的子节点；
      3. 调用`parser()`；
   4. 分析方法末尾，先输出当前成分类型（终结符先输出当前`token`），再更新父节点的`tokenIndex`（终结符保留当前tokeIndex指向对应`token`，直接更新父节点`index+1`）；
2. 设计`Parser`类：
   - 包含`ArrayList<Token> tokens`表示传入的`token`流；
   - 包含`ComUnit root`表示初始根节点；
3. 对于类似`AddExp → MulExp | AddExp ('+' | '−') MulExp`的分析，由于存在左递归，                                                                     将其转化成`MulExp { ('+' | '−') MulExp }`，但是位于运算符左边的成分实际上是`AddExp`而不是`MulExp`，所以如果存在运算符，应在左操作数调用`MulExp`的分析方法后输出成分`<AddExp>`，（仅用于输出，语法树的构造无需额外子节点`AddExp`）。
