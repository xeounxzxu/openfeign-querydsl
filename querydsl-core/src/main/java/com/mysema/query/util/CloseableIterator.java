package com.mysema.query.util;

import java.io.Closeable;
import java.util.Iterator;

/**
 * CloseableIterator provides
 *
 * @author tiwe
 * @version $Id$
 */
public interface CloseableIterator<T> extends Iterator<T>,Closeable{

}
