/******************************************************************************
* Copyright 2013-2014 LASIGE                                                  *
*                                                                             *
* Licensed under the Apache License, Version 2.0 (the "License"); you may     *
* not use this file except in compliance with the License. You may obtain a   *
* copy of the License at http://www.apache.org/licenses/LICENSE-2.0           *
*                                                                             *
* Unless required by applicable law or agreed to in writing, software         *
* distributed under the License is distributed on an "AS IS" BASIS,           *
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    *
* See the License for the specific language governing permissions and         *
* limitations under the License.                                              *
*                                                                             *
*******************************************************************************
* Selector that uses (simulated) user interaction (from the Oracle) to help   *
* perform 1-to-1 selection.                                                   *
*                                                                             *
* @author Aynaz Taheri, Daniel Faria                                          *
* @date 23-08-2014                                                            *
* @version 2.0                                                                *
******************************************************************************/
package aml.filter;

import java.util.Set;

import aml.AML;
import aml.match.Alignment;
import aml.match.Mapping;
import aml.ontology.URIMap;
import aml.settings.SelectionType;
import aml.util.Oracle;

public class InteractiveSelector implements Selector
{
	
//Attributes
	
	//The oracle used for simulated user feedback
	private Oracle oracle;
	//The alignments used as support to decide
	//whether to ask for user feedback
	private Set<Alignment> aligns;
	//Auxiliary variables
	private double[] previousSignatureVector;
	private int previousFeedback;
	private boolean previousAgreement;
	private int trueCount;
	private int falseCount;
	
//Constructors
	
	public InteractiveSelector(Oracle o, Set<Alignment> a)
	{
		oracle = o;
		aligns = a;
		previousSignatureVector = new double[aligns.size()];
		previousFeedback = 0;
		previousAgreement = false;
	}
	
//Public Methods
	
	public int negativeInteractions()
	{
		return falseCount;
	}
	
	public int positiveInteractions()
	{
		return trueCount;
	}	
	
	@Override
	public Alignment select(Alignment a, double thresh)
	{
		long time = System.currentTimeMillis()/1000;
		System.out.println("Performing Interactive Selection");
		AML aml = AML.getInstance();
		URIMap map = aml.getURIMap();
		
		RankedSelector s = new RankedSelector(SelectionType.STRICT);
		Alignment maps = s.select(a,thresh);
		Alignment selected = new Alignment();
		trueCount = 0;
		falseCount = 0;
		for(Mapping m : maps)
		{
			double sim = m.getSimilarity();
			if(sim >= 0.7)
				selected.add(m);
			else
			{
				String sourceURI = map.getURI(m.getSourceId());
				String targetURI = map.getURI(m.getTargetId());

				if(disagreement(m.getSourceId(),m.getTargetId()))
				{
					if(oracle.check(sourceURI,targetURI,m.getRelationship()))
					{
						selected.add(m);
						trueCount++;
						previousFeedback=1;
					}
					else
					{
						falseCount++;
						previousFeedback=-1;
					}
				}

			}
			if(falseCount > a.size() * 0.35)
				break;
		}
		time = System.currentTimeMillis()/1000 - time;
		System.out.println("Finished in " + time + " seconds");
		System.out.println("Total Oracle Input: " + (trueCount+falseCount) +
				"; Positive Oracle Input: " + trueCount);

		return selected;
	}
	
//Private Methods
	
	//Checks if there is disagreement between the support
	//alignments for a given class pair
	private boolean disagreement(int sourceId, int targetId)
	{
		double[] signatureVector = new double[aligns.size()];
		int index = 0;
		for(Alignment a : aligns)
			signatureVector[index++] = a.getSimilarity(sourceId,targetId);
		
		double variance = variance(signatureVector);

		if(variance < 0.04)
		{
			previousAgreement = false;
			previousFeedback = 0;
			previousSignatureVector = signatureVector;
			return false;
		}
		else if(previousAgreement == true && previousFeedback == -1 &&
					previousSignatureVector == signatureVector)
		{
			previousAgreement = true;
			previousFeedback = -1;
			previousSignatureVector = signatureVector;
			return false;
		}
		else
		{
			previousAgreement = true;
			previousSignatureVector = signatureVector;
			return true;
		}
	}
	
	//Measures the variance between elements of an array
	private double variance(double[] v)
	{
		double average = 0.0;
		for(double d : v)
			average += d;
		average /= v.length;
		double variance = 0.0;
		for(double d : v)
			variance += Math.pow(d - average,2);
		return variance / (v.length - 1);
	}
}