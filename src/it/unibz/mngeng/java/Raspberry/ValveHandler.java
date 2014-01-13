package it.unibz.mngeng.java.Raspberry;

import java.io.IOException;
import java.util.EnumSet;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiGpioProvider;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.impl.PinImpl;

import it.unibz.mngeng.java.Commons.Parameters;
import it.unibz.mngeng.java.DBUtility.Areas;
import it.unibz.mngeng.java.Exceptions.RMException;
import it.unibz.mngeng.java.Handlers.DataStructures;

public class ValveHandler extends Thread
{
	final GpioController gpio = GpioFactory.getInstance();
	private GpioPinDigitalOutput pin;

	private DataStructures appData;
	private Parameters parms;
	private boolean shutDown;
	private int instance;
	private Areas areaData;
	private String pinName;
	private int secondsElapsed= 0;

	public ValveHandler(DataStructures appData, int instance, boolean shutDown, Parameters parms) throws RMException
	{
		this.appData = appData;
		this.shutDown = shutDown;
		this.instance = instance;
		this.parms = parms;
		
		this.areaData = new Areas();
		this.areaData.populateObject("SELECT * " +
									 "FROM Areas " +
									 "WHERE fieldId = " + this.parms.getFieldId() + " AND " + 
									 "      sensorId = " + instance, areaData);
		pinName = "Area_" + this.parms.getFieldId() + "." + areaData.getSensorId();
		Pin pinToUse;
		if (instance == 0)
		{
			pinToUse = RaspiPin.GPIO_04;
		}
		else
		{
			pinToUse = RaspiPin.GPIO_05;
		}
//		Pin pinDescr = new PinImpl(RaspiGpioProvider.NAME, areaData.getSensorId(), pinName, 
//				                   EnumSet.of(PinMode.DIGITAL_INPUT, PinMode.DIGITAL_OUTPUT),
//				                   PinPullResistance.all()); 
		pin = gpio.provisionDigitalOutputPin(pinToUse, pinName, PinState.HIGH);
	}

	public ValveHandler(DataStructures appData, int instance, boolean shutDown, Parameters parms, Areas areaData) throws RMException
	{
		this.appData = appData;
		this.shutDown = shutDown;
		this.instance = instance;
		this.parms = parms;
		
		this.areaData = areaData;
		pinName = "Area_" + this.parms.getFieldId() + "." + instance;
		Pin pinDescr = new PinImpl(RaspiGpioProvider.NAME, 1, pinName, 
                EnumSet.of(PinMode.DIGITAL_INPUT, PinMode.DIGITAL_OUTPUT),
                PinPullResistance.all()); 
		pin = gpio.provisionDigitalOutputPin(pinDescr, pinName, PinState.LOW);
	}

	@Override
	public void run() 
	{
		while(!shutDown)
		{
			if ((appData.getMoisture(instance) < areaData.getMoistureMin()) &&
				!appData.getValveStatus(instance))
			{
				try
				{
					appData.setWateringTimeElapsed(0, instance, true);
					appData.setValveStatus(instance, true);
					secondsElapsed = 0;
				}
				catch (IOException e) 
				{
					System.out.println("ValveHandler instance " + instance + ": got IOException " + e.getMessage());
					System.exit(-1);
				}
			}

			if (appData.getValveStatus(instance))
			{
				pin.low();
				secondsElapsed++;
				if (secondsElapsed % 60 == 0)
				{
					try
					{
						int i = appData.getWateringTimeElapsed(instance);
						appData.setWateringTimeElapsed(++i, instance, true);
						if (i >= areaData.getWateringTime())
						{
							appData.setWateringTimeElapsed(instance, 0, true);
							appData.setValveStatus(instance, false);
							secondsElapsed = 0;
							pin.high();
						}
					}
					catch (IOException e) 
					{
						System.out.println("ValveHandler instance " + instance + ": got IOException " + e.getMessage());
						System.exit(-1);
					}
				}
			}
			else
			{
				pin.high();
			}
			
			try 
			{
				Thread.sleep(1000);
			} 
			catch (InterruptedException e) 
			{
				;
			}
		}
	}
}
