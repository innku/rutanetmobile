package com.rho;

import java.io.IOException;

import j2me.io.FileNotFoundException;

public interface IRAFile {
	public void open(String name) throws FileNotFoundException;
	public void open(String name, String mode) throws FileNotFoundException;
	public void close() throws IOException;
	
	public long size() throws IOException;
	public void setSize(long newSize) throws IOException;
	
	public void seek(long pos) throws IOException;
	public long seekPos() throws IOException;
	
	public void write(int b) throws IOException;
	public void write(byte[] b, int off, int len) throws IOException;
	
	public int read() throws IOException;
	public int read(byte[] b, int off, int len) throws IOException;
	
	public void sync() throws IOException;
	
	public void listenForSync(String name) throws IOException;
	public void stopListenForSync(String name) throws IOException;
	
	public boolean exists();
	public void delete() throws IOException;
	public void rename(String newName) throws IOException;
}
