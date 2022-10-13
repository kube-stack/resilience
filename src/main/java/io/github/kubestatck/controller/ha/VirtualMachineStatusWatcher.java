/**
 * Copyright (2022, ) Institute of Software, Chinese Academy of Sciences
 */
package io.github.kubestatck.controller.ha;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.kubestack.client.KubeStackClient;
import io.github.kubestack.client.api.models.Node;
import io.github.kubestack.client.api.models.VirtualMachine;
import io.github.kubestack.client.api.specs.virtualmachine.Lifecycle.StartVM;
import io.github.kubestatck.controller.ha.NodeSelectorImpl.Policy;
import io.github.kubesys.client.KubernetesClient;
import io.github.kubesys.client.KubernetesWatcher;

/**
 * @author shizhonghao17@otcaix.iscas.ac.cn
 * @author yangchen18@otcaix.iscas.ac.cn
 * @author wuheng@otcaix.iscas.ac.cn
 * @since Wed May 01 17:26:22 CST 2019
 * 
 *        https://www.json2yaml.com/ http://www.bejson.com/xml2json/
 * 
 *        debug at runWatch method of
 *        io.fabric8.kubernetes.client.dsl.internal.WatchConnectionManager
 **/
public class VirtualMachineStatusWatcher extends KubernetesWatcher {

	protected final static Logger m_logger = Logger.getLogger(VirtualMachineStatusWatcher.class.getName());
	
	protected final NodeSelectorImpl nsl;
	
	public VirtualMachineStatusWatcher(KubernetesClient client) {
		super(client);
		this.nsl = new NodeSelectorImpl((KubeStackClient) client);
	}

	protected boolean invalidNodeStatus(Node node) {
		return node == null 
				|| NodeSelectorImpl.isMaster(node) 
				|| NodeSelectorImpl.notReady(node) 
				|| NodeSelectorImpl.unSched(node);
	}

	protected Node getNode(String nodeName) {
		try {
			return ((KubeStackClient)client).nodes().get(nodeName);
		} catch (Exception ex) {
			return null;
		}
	}

	protected boolean isShutDown(Map<String, Object> status) {
		return status.get("reason").equals("Shutdown");
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> getStatus(VirtualMachine vm) {
		Map<String, Object> statusProps = vm.getSpec().getStatus().getAdditionalProperties();	
		Map<String, Object> statusCond = (Map<String, Object>) (statusProps.get("conditions"));
		Map<String, Object> statusStat = (Map<String, Object>) (statusCond.get("state"));
		return (Map<String, Object>) (statusStat.get("waiting"));
	}


	@Override
	public void doAdded(JsonNode node) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void doModified(JsonNode node) {
		VirtualMachine vm = new ObjectMapper().convertValue(node, VirtualMachine.class);
		
		String ha = vm.getMetadata().getLabels().get("ha");
		// VM without HA setting
		if (ha == null || ha.length() == 0 
					|| !ha.equals("true")) {
			return;
		}

		// this vm is running or the vm is not marked as HA
		if (isShutDown(getStatus(vm))) {
			
			// get nodeName
			String nodeName = vm.getSpec().getNodeName();
			
			String newNode = invalidNodeStatus(getNode(nodeName)) ? 
					nsl.getNodename(Policy.minimumCPUUsageHostAllocatorStrategyMode, nodeName, null) : nodeName;
			
			// just start VM
			try {
				if (nodeName.equals(newNode)) {
					((KubeStackClient) client).virtualMachines().startVM(
							vm.getMetadata().getName(), new StartVM());
				} else {
					((KubeStackClient)client).virtualMachines().startVM(
							vm.getMetadata().getName(), nodeName, new StartVM());
				}
			} catch (Exception e) {
				m_logger.log(Level.SEVERE, "cannot start vm for " + e);
			}
		}
		
	}


	@Override
	public void doDeleted(JsonNode node) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void doClose() {
		m_logger.log(Level.INFO, "Stop VirtualMachineStatusWatcher");
	}

}
