/**
 * Copyright (2022, ) Institute of Software, Chinese Academy of Sciences
 */
package io.github.kubestatck.controller;

import java.io.File;
import java.util.logging.Logger;

import io.github.kubestack.client.KubeStackClient;
import io.github.kubestatck.controller.ha.NodeStatusWatcher;
import io.github.kubestatck.controller.ha.VirtualMachineStatusWatcher;

/**
 * @author wuheng@otcaix.iscas.ac.cn
 * 
 * @version 2.0.0
 * @since   2022/10/13
 * 
 * KubevirtController is used for starting various wacthers.
 * 
 * Note that this progress is running on the master node of Kubernetes
 * with pre-installed CRDs.
 **/
public final class KubestackController {
	
	/**
	 * m_logger
	 */
	protected final static Logger m_logger  = Logger.getLogger(KubestackController.class.getName());

	/**
	 * default token
	 */
	public final static String DEFAULT_TOKEN = "/etc/kubernetes/admin.conf";
	
	/**
	 * Kubernetes client
	 */
	protected final KubeStackClient client;
	
	/************************************************************************
	 * 
	 *                       Constructors
	 * 
	 ************************************************************************/
	
	/**
	 * initialize the client with the default token
	 * 
	 * @throws Exception         exception
	 */
	public KubestackController() throws Exception {
		this(DEFAULT_TOKEN);
	}
	
	/**
	 * initialize the client with the specified token
	 * 
	 * @param  token              token
	 * @throws Exception          exception
	 */
	public KubestackController(String token) throws Exception {
		this.client = new KubeStackClient(new File(token));
	}
	

	/************************************************************************
	 * 
	 *                       Core
	 * 
	 ************************************************************************/
	/**
	 * start all watchers based on Java reflect mechanism
	 * 
	 * @throws Exception               exception 
	 */
	public void startAllWatchers() throws Exception {
		
		client.watchNodes(new NodeStatusWatcher(client));
		client.watchVirtualMachineImages(new VirtualMachineStatusWatcher(client));
	}

	

	/************************************************************************
	 * 
	 *                       Main
	 * 
	 ************************************************************************/
	/**
	 * @param  args               args
	 * @throws Exception          cannot start controller manager
	 */
	public static void main(String[] args) throws Exception {
		KubestackController controller = new KubestackController();
		controller.startAllWatchers();
	}
}
