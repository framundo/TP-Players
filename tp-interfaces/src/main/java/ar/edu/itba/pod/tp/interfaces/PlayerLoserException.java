/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ar.edu.itba.pod.tp.interfaces;

import java.rmi.RemoteException;

@SuppressWarnings("serial")
public class PlayerLoserException extends RemoteException
{

	public PlayerLoserException(String s)
	{
		super(s);
	}

}
