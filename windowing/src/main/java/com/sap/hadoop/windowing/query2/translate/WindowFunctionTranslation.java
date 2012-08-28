package com.sap.hadoop.windowing.query2.translate;

import java.util.ArrayList;

import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;

import com.sap.hadoop.windowing.WindowingException;
import com.sap.hadoop.windowing.query2.definition.ArgDef;
import com.sap.hadoop.windowing.query2.definition.QueryDef;
import com.sap.hadoop.windowing.query2.definition.TableFuncDef;
import com.sap.hadoop.windowing.query2.definition.WindowFunctionDef;
import com.sap.hadoop.windowing.query2.specification.WindowFunctionSpec;
import com.sap.hadoop.windowing.query2.translate.QueryTranslationInfo.InputInfo;

public class WindowFunctionTranslation
{
	static WindowFunctionDef translate(QueryDef qDef, TableFuncDef windowTableFnDef, WindowFunctionSpec wSpec) throws WindowingException
	{
		QueryTranslationInfo tInfo = qDef.getTranslationInfo();
		InputInfo iInfo = tInfo.getInputInfo(windowTableFnDef); 

		WindowFunctionDef wFnDef = new WindowFunctionDef();
		wFnDef.setSpec(wSpec);
		
		/*
		 * translate args
		 */
		ArrayList<ASTNode> args = wSpec.getArgs();
		if ( args != null)
		{
			for(ASTNode expr : args)
			{
				ArgDef argDef = translateTableFunctionArg(qDef, windowTableFnDef, iInfo,  expr);
				wFnDef.addArg(argDef);
			}
		}
		
		setupEvaluator(wFnDef);
		
		return wFnDef;
	}
	
	static void setupEvaluator(WindowFunctionDef wFnDef) throws WindowingException
	{
		try
		{
			WindowFunctionSpec wSpec = wFnDef.getSpec();
			ArrayList<ArgDef> args = wFnDef.getArgs();
			ArrayList<ObjectInspector> argOIs = getWritableObjectInspector(args);
			GenericUDAFEvaluator wFnEval = org.apache.hadoop.hive.ql.exec.FunctionRegistry.getGenericUDAFEvaluator(wSpec.getName(), argOIs, wSpec.isDistinct(), wSpec.isStar());
			ObjectInspector[] funcArgOIs = null;
			
			if ( args != null)
			{
				funcArgOIs = new ObjectInspector[args.size()];
				int i = 0;
				for(ArgDef arg : args)
				{
					funcArgOIs[i++] =arg.getOI();
				}
			}
			
			ObjectInspector OI = wFnEval.init(GenericUDAFEvaluator.Mode.COMPLETE, funcArgOIs);
			
			wFnDef.setEvaluator(wFnEval);
			wFnDef.setOI(OI);
		}
		catch(HiveException he)
		{
			throw new WindowingException(he);
		}
	}
	
	private static ArgDef translateTableFunctionArg(QueryDef qDef, TableFuncDef tDef, InputInfo iInfo, ASTNode arg) throws WindowingException
	{
		return TranslateUtils.buildArgDef(qDef, iInfo, arg);
	}
	
	static ArrayList<ObjectInspector> getWritableObjectInspector(ArrayList<ArgDef> args)
	{
		ArrayList<ObjectInspector> result = new ArrayList<ObjectInspector>();
		for (ArgDef arg : args)
		{
			ExprNodeDesc expr = arg.getExprNode();
			result.add(expr.getWritableObjectInspector());
		}
		return result;
	}
}