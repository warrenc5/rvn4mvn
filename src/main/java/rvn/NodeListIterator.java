/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rvn;

import java.util.Iterator;
import org.w3c.dom.NodeList;

/**
 *
 * @author wozza
 */
public class NodeListIterator<T> implements Iterator<T> {

    private final NodeList nl;
    private int count;

    public NodeListIterator(NodeList nodeList) {
        this.nl = nodeList;
    }
    

    @Override
    public boolean hasNext() {
        return count < nl.getLength();

    }

    @Override
    public T next() {
        return (T) nl.item(count++);
    }
}