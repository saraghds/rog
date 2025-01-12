package com.buzzfuzz.rog.traversal;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.buzzfuzz.rog.decisions.RNG;
import com.buzzfuzz.rog.ROG;
import com.buzzfuzz.rog.decisions.Choice;
import com.buzzfuzz.rog.decisions.Constraint;
import com.buzzfuzz.rog.decisions.Target;

public class InstanceDispatcher {

	private Set<ClassPkg> history;
	private Target context;
	private Constraint constraint;
	private ROG robjg;
	private RNG rng;

	public InstanceDispatcher(ROG rog) {
		this(new RNG(), rog);
	}

	public InstanceDispatcher(RNG rng, Set<ClassPkg> chain, ROG robjg) {
		this.rng = rng;
		this.history = chain == null ? new LinkedHashSet<ClassPkg>() : new LinkedHashSet<ClassPkg>(chain);
		this.robjg = robjg;
	}

	public InstanceDispatcher(RNG rng, ROG robjg) {
		this(rng, null, robjg);
	}

	public InstanceDispatcher(InstanceDispatcher dispatcher) {
		this(dispatcher.rng, dispatcher.history, dispatcher.robjg);
	}

	public InstanceDispatcher(InstanceFinder finder) {
		this(finder.rng, finder.history, finder.rog);
	}

	public Set<ClassPkg> getHistory() {
		return history;
	}

	public RNG getRNG() {
		return rng;
	}

	public ROG getROG() {
		return robjg;
	}

	private void log(String msg) {
		int indent = history.size();
		while (indent > 0) {
			msg = "    " + msg;
			indent--;
		}
		rng.log(msg + '\n');
	}

	public Object tryGetInstance(Type type) {
		ClassPkg target = parseType(type);

		if (history.contains(target))
			return null;

		// Maybe in load method?
		history.add(target);
		loadConstraint(getContext(target.getClazz()));

		if (!target.getClazz().isPrimitive() && constraint.isNull() != null && constraint.isNull()) {
			log("Returning null instead of instance");
			Choice nullChoice = new Choice();
			nullChoice.setTarget(this.context);
			nullChoice.setValue();
			rng.getConfig().getChoices().add(nullChoice);
			return null;
		}

		return getInstance(target);
	}

	public Object getInstance(Type type) {
		// This is probably an indication that I can't get the same kind of generics
		// information from just getClass().
		// Which makes sense.
		// The end solution is probably to reserve this method to only work for
		// non-generic types and then
		// Research ways to reference a field or some kind of lightweight object
		// Could even automatically make a field a type within my own custom class to
		// make it easier...
		return getInstance(parseType(type));
	}

	private Object getInstance(ClassPkg target) {

		// Might need to move history check here

		// If this method was called directly
		if (constraint == null) {
			history.add(target);
			loadConstraint(getContext(target.getClazz()));
		}

		Object instance = checkPrimatives(target.getClazz());

		if (instance == null) {
			instance = checkCommon(target);
		}

		if (instance == null) {
			// Eventually will need full ClassPkg
			instance = checkClasses(target.getClazz());
		}
		return instance;
	}

	private Target getContext(Class<?> target) {
		this.context = new Target();
		String instancePath = "";
		for (ClassPkg instance : history) {
			instancePath += instance.getClazz().getSimpleName();
			if (instance.getGenerics() != null && instance.getGenerics().length != 0) {
				instancePath += '<';
				for (Type generic : instance.getGenerics()) {
					instancePath += generic.getTypeName().substring(generic.getTypeName().lastIndexOf('.') + 1) + ',';
				}
				instancePath = instancePath.substring(0, instancePath.length() - 1);
				instancePath += '>';
			}
			instancePath += '.';
		}
		instancePath = instancePath.substring(0, instancePath.length() - 1);
		context.setInstancePath(instancePath);
		log("current path: " + instancePath);

		context.setTypeName(target.getSimpleName());

		return context;
	}

	private void loadConstraint(Target target) {
		Constraint constraint = rng.getConstraint(target);

		if (constraint == null) {
//			System.out.println("MAKING NEW CONSTRAINT");
			constraint = rng.makeConstraint(target);
		}
//		else System.out.println("USING EXISTING CONSTRAINT" + constraint.toString());
		this.constraint = constraint;
	}

	public Object checkClasses(Class<?> target) {

		Object inst = new FuzzConstructorFinder(this).findInstance(target);
		if (inst == null) {
			inst = new ConstructorFinder(this).findInstance(target);
			if (inst == null) {
				inst = new LocalFactoryFinder(this).findInstance(target);
				if (inst == null) {
					inst = new FactoryFinder(this).findInstance(target);
					if (inst == null) {
						inst = new SubclassFinder(this).findInstance(target);
						if (inst == null) {
							inst = createInstanceFromFields(target);
							if (inst == null)
								log("Could not find a way to get an instance of this class.");
						}
					}
				}
			}
		}
		return inst;
	}

	public Object createInstanceFromFields(Class<?> target) {
		Constructor[] ctors = target.getDeclaredConstructors();
		Constructor ctor = null;
		for (int i = 0; i < ctors.length; i++) {
			ctor = ctors[i];
			if (ctor.getGenericParameterTypes().length == 0)
				break;
		}

		ctor.setAccessible(true);
		Object obj = null;
		try {
			obj = ctor.newInstance();

			Field[] fields = target.getDeclaredFields();
			for (Field field : fields) {
				field.setAccessible(true);
				try {
					Object fieldObj = getInstance(field.getGenericType());
					field.set(obj, fieldObj);
				} catch (Exception e) {
					continue;
				}
			}
		} catch (Exception e) {
		}

		return obj;
	}

	public Object checkPrimatives(Class<?> target) {
		if (target.equals(int.class)) {
			return rng.getInt(this.context, this.constraint);
		} else if (target.equals(long.class)) {
			return rng.getLong(this.context, this.constraint);
		} else if (target.equals(char.class)) {
			return rng.getChar(this.context, this.constraint);
		} else if (target.equals(float.class)) {
			return rng.getFloat(this.context, this.constraint);
		} else if (target.equals(double.class)) {
			return rng.getDouble(this.context, this.constraint);
		} else if (target.equals(boolean.class)) {
			return rng.getBool(this.context, this.constraint);
		} else if (target.equals(byte.class)) {
			return rng.getByte(this.context, this.constraint);
		} else if (target.equals(short.class)) {
			return rng.getShort(this.context, this.constraint);
		} else if (target.equals(String.class)) {
			return rng.getString(this.context, this.constraint);
		} else if (target.isEnum()) {
			return rng.getEnum(this.context, this.constraint, target);
		} else {
			return null;
		}
	}

	private Object checkCommon(ClassPkg target) {

		if (target.getClazz().isArray()) {
			Class<?> type = target.getClazz().getComponentType();
			return randomArray(type);
		} else if (target.getClazz().equals(List.class) || target.getClazz().equals(ArrayList.class)) {
//			log(String.valueOf(target.getGenerics() == null));
			ClassPkg type = parseType(target.getGenerics()[0]);

			// Unfortunately this needs to be separate from the randArray method because
			// there can be arrays of primitives but not Lists of primitives
			int length = rng.fromRange(0, 10);
			Object[] array = (Object[]) Array.newInstance(type.getClazz(), length);
			for (int i = 0; i < length; i++) {
				Object instance = new InstanceDispatcher(this).getInstance(type);
				if (instance == null) {
					return null;
				}
				array[i] = instance;
			}
			return Arrays.asList(array);
		} else if (target.getClazz().equals(BigInteger.class)) {
			return new BigInteger(rng.fromRange(2, 32), rng.getRandom());
		} else if (target.getClazz().equals(Number.class)) {
			return rng.getDouble();
		}
		return null;
	}

	private Object randomArray(ClassPkg type) {
		int length = rng.fromRange(0, 10);
		Object array = Array.newInstance(type.getClazz(), length);
		for (int i = 0; i < length; i++) {
			Object instance = new InstanceDispatcher(this).getInstance(type);
			if (instance == null) {
				return null;
			}
			Array.set(array, i, instance);
		}
		return array;
//		return Array.newInstance(type.getClazz(), 0).getClass().cast(array);
	}

	private Object randomArray(Class<?> type) {
		return randomArray(new ClassPkg(type, null));
	}

	public static ClassPkg parseType(Type type) {
		ClassPkg pkg;
		if (type instanceof Class) {
			// This doesn't have generics.
			pkg = new ClassPkg((Class<?>) type, null);
		} else if (type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			// Eventually will have to store ClassPkgs here to account for
			// List<List<Integer>>
			Type[] generics = new Type[pt.getActualTypeArguments().length];
			for (int j = 0; j < pt.getActualTypeArguments().length; j++) {
				Type gtype = pt.getActualTypeArguments()[j];
				if (gtype instanceof WildcardType) {
					// Generic is in "? extends Class" format. We want its upperbound
					WildcardType wc = (WildcardType) gtype;
					// if (wc.getUpperBounds()[0] == null)
					// log("New kind of wildcard");
					generics[j] = wc.getUpperBounds()[0];
				} else {
					// Generic is a normal class at this point (probably)
					generics[j] = gtype;
				}
			}
			pkg = new ClassPkg((Class<?>) pt.getRawType(), generics);
		} else {
			// TODO: This is a generic type E. Will need to feed in a random object
			pkg = new ClassPkg(String.class, null);
		}

		return pkg;
	}

	public static ClassPkg[] packageClasses(Type[] genArgs) {
		// TODO: Sometimes target or generics is null. Maybe this is because of cases
		// like 'E'

		// We are creating one classPkg per argument
		ClassPkg[] pkgs = new ClassPkg[genArgs.length];

		// Proposing new way to do generics based on what I know now
		for (int i = 0; i < genArgs.length; i++) {
			Type type = genArgs[i];
			pkgs[i] = parseType(type);
		}

		return pkgs;
	}

	// I can probably make methods like these private. It would be best...
	public Object[] randomArgs(Type[] genArgs) {

		Object[] instances = new Object[genArgs.length];
		for (int i = 0; i < genArgs.length; i++) {
			instances[i] = new InstanceDispatcher(this).tryGetInstance(genArgs[i]);
			// If any of the arguments return BadPath, return BadPath
//			if (instances[i].getClass().)
//				return null;
		}
		return instances;
	}

}
