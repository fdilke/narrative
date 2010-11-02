/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenRewriteStream;

/**
 * Helper class to build trees representing the parsing process of a grammar.
 * 
 * @author Yang Jiang (yang.jiang.z@gmail.com)
 *
 */
public class TreeBuilder {
    private static final long serialVersionUID = 1L;
    
    Stack<TreeNode> stacks = new Stack<TreeNode>();
    String sourceFile;
    String fileContent;
    TreeNode root;
    TreeNode currentParent;
    
    public static void main(String[] args) {
        TreeBuilder treeBuilder = new TreeBuilder(args[0]);
        treeBuilder.visit();
    }

    public TreeBuilder(String file) {
        loadFile(file);
        buildTree();
    }

    public void visit() {
        visit(root);
    }

    public void visit(TreeNode node) {
        for (TreeNode child : node.children) {
            if (child.isTestMethod()) {
                print(child, 0);
            } else {
                visit(child);
            }
        }
    }

    public void print(TreeNode node, int level) {
        if (node.isLiteral()) { 
            /*for (int i=0; i<level*2; i++) { System.out.print(" "); }*/
            System.out.println(node.toString());
        }
        for (TreeNode child : node.children) {
            if (child.text.equals("modifiers")) { continue; }
            print(child, level+1);
        }
    }

    /**
     * Set the current parent of the node. Any node added after this call we be 
     * counted as children of the newParent.
     * 
     * @param newParent
     * @return
     */
    public TreeNode setCurrentParent(TreeNode newParent) {
        TreeNode oldParent = currentParent;
        currentParent = newParent;
        return oldParent;
    }

    public TreeNode getCurrentParent() {
        return currentParent;
    }

    /**
     * Add a child to the currentParent set by calling setCurrentParent().
     * @param name
     * @return
     */
    public TreeNode addNode(String name, Token start, Token stop) {
        TreeNode node = new TreeNode(name);
        if (currentParent != null)
            currentParent.add(node);
        node.setLeaf(false);
        return node;
    }

    public TreeNode addNode(String name) {
        TreeNode node = new TreeNode(name);
        node.setLeaf(false);
        if (currentParent != null)
            currentParent.add(node);

        return node;
    }

    /**
     * Same as addNode(), except that a leaf should contain no child.
     * @param name
     * @return
     */
    public TreeNode addLeaf(String name, Token token) {
        TreeNode node = new TreeNode(name);
        node.setLeaf(true);
        if (currentParent != null)
            currentParent.add(node);
        return node;
    }

    /**
     * Stores the current parent in the stack, this is usually called before setting a new parent.
     */
    public void pushTop() {
        stacks.push(currentParent);
    }

    /**
     * Restores the previous parent.
     */
    public TreeNode popTop() {
        TreeNode ret = currentParent;
        currentParent = stacks.pop();
        return ret;
    }

    private void loadFile(String file) {
        FileInputStream in = null;
        this.sourceFile = file;
        fileContent = null;
        try {
            in = new FileInputStream(file);
            byte[] buf = new byte[10240];
            StringBuffer sbuf = new StringBuffer(buf.length);
            int len = in.read(buf);
            while (len > 0) {
                sbuf.append(new String(buf, 0, len));
                len = in.read(buf);
            }
            fileContent = sbuf.toString();
        } catch (Exception e) {
            fileContent = null;
        } finally {
            try {
                in.close();
            } catch (Exception e) {
            }
        }

    }

    private void buildTree() {
        root = new TreeNode("compilationUnit [" + sourceFile + "]");
        try {
            JavaLexer lex = new JavaLexer(new ANTLRStringStream(fileContent));
            TokenRewriteStream tokens = new TokenRewriteStream(lex);
            JavaParser g = new JavaParser(tokens);
            g.setTreeBuilder(this);
            this.setCurrentParent(root);
            g.compilationUnit();                        
            if (g.getNumberOfSyntaxErrors() != 0) {
                throw new IllegalStateException("There are syntax errors in the input file. [source:" + sourceFile + "]");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("There are syntax errors in input file. [source:" + sourceFile + "]");
        }
    }
}

class TreeNode {
    private static final long serialVersionUID = 1L;
    private static Pattern literalPattern = Pattern.compile(".*\\[['\"]*([^'^\"]*)['\"]*\\]");
    String text;
    private boolean isLeaf = false;
    List<TreeNode> children = new ArrayList<TreeNode>();

    public TreeNode(String text) {
        this.text = text;
    }

    public void add(TreeNode node) {
        children.add(node);
    }

    public void setLeaf(boolean isLeaf) {
        this.isLeaf = isLeaf;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public boolean isLiteral() {
        return literalPattern.matcher(text).matches();
    }

    public TreeNode getFirstChildByName(String name) {
        for (TreeNode child : children) {
            if (child.text.equals(name)) { return child; }
        }
        return null;
    }

    public boolean isTestMethod() {
        if (! text.equals("methodDeclaration")) { return false; }
        TreeNode modifiers      = this.         getFirstChildByName("modifiers");          if (null == modifiers)      { return false; }
        TreeNode annotation     = modifiers.    getFirstChildByName("annotation");         if (null == annotation)     { return false; }
        TreeNode qualifiedName  = annotation.   getFirstChildByName("qualifiedName");      if (null == qualifiedName)  { return false; }
        TreeNode testAnnotation = qualifiedName.getFirstChildByName("IDENTIFIER['Test']"); if (null == testAnnotation) { return false; }
        return true;
    }
    
    public String toString() { 
        Matcher literalMatcher = literalPattern.matcher(text);
        if (literalMatcher.matches()) { return literalMatcher.group(1); } else { return text; }
    }
}