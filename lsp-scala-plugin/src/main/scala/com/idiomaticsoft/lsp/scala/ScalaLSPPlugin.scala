package com.idiomaticsoft.lsp.scala

import org.osgi.framework.BundleContext
import org.eclipse.ui.plugin.AbstractUIPlugin
import java.io.File
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import org.eclipse.debug.core.ILaunchManager
import org.eclipse.debug.core.model.IProcess
import org.eclipse.debug.core.model.RuntimeProcess
import com.idiomaticsoft.lsp.scala.edit.MetalsProcess
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.launching.IRuntimeClasspathEntry
import org.eclipse.debug.core.model.IStreamsProxy2
import org.eclipse.debug.internal.core.StreamsProxy
import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.ICoreRunnable
import org.eclipse.core.runtime.IProgressMonitor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch


object ScalaLSPPlugin {

  @volatile private var plugin: ScalaLSPPlugin = _
  def apply(): ScalaLSPPlugin = plugin

} 

class ScalaLSPPlugin extends AbstractUIPlugin {


  @volatile var job: Job = null

  val launchedJob = new AtomicBoolean(false)

  var scheduledJob = new CountDownLatch(1)
  
  @volatile var launch: ILaunch = null

  override def start(context: BundleContext) = {
    ScalaLSPPlugin.plugin = this 
    super.start(context)
  }

  def launchJob(): Unit = {
		val manager = DebugPlugin.getDefault().getLaunchManager()
		val typeConfiguration = manager.getLaunchConfigurationType("com.idiomaticsoft.lsp.scala.languageServer.launchConfType")
		val wc = typeConfiguration.newInstance(null, "Metals config")
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "scala.meta.metals.Main")
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, false)
		wc.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, false)
		val vmParams = "-XX:+UseG1GC -XX:+UseStringDeduplication -Xss4m -Xms100m -Xmx2G"
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, wc.getAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, vmParams))
		wc.setAttribute(DebugPlugin.ATTR_PROCESS_FACTORY_ID,"com.idiomaticsoft.lsp.scala.metalsprocess")
		val config = wc.doSave()
		job = Job.create("Running Metals server", new ICoreRunnable {
			def run(monitor: IProgressMonitor) {
				launch = config.launch(ILaunchManager.RUN_MODE, monitor)
			}
		})	
		job.schedule()	
  }

  def processForCommand(): IProcess = {
	if (Option(launch).isDefined && launch.getProcesses().toList.find(x => !x.isTerminated()).isEmpty) {
		if (launchedJob.compareAndSet(true, false)) {
			println("Resetting latch")
			scheduledJob = new CountDownLatch(1)
		}
	}
	if (!launchedJob.getAndSet(true)) {
		launchJob()
		scheduledJob.countDown()
	}
	scheduledJob.await()
	job.join()
	val someProcess = launch.getProcesses().toList.find(x => !x.isTerminated())
	someProcess.getOrElse(null) 
  }
}
