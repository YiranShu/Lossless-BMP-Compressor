import java.util.ArrayList;

public class HashableArray {
    private ArrayList<Integer> array;

    public HashableArray() {
        array = new ArrayList<>();
    }

    public HashableArray(int elem) {
        array = new ArrayList<>();
        array.add(elem);
    }

    public HashableArray(HashableArray h) {
        ArrayList<Integer> a = h.getArray();
        array = new ArrayList<>();

        array.addAll(a);
    }

    public ArrayList<Integer> getArray() {
        return array;
    }

    public void add(int elem) {
        array.add(elem);
    }

    public HashableArray append(int elem) {
        HashableArray h = new HashableArray(this);
        h.add(elem);

        return h;
    }

    @Override
    public int hashCode() {
        int res = 0;

        for(int item: array) {
            res += item;
        }

        return res;
    }

    @Override
    public boolean equals(Object object) {
        if(this == object) {
            return true;
        } else if(object == null) {
            return false;
        } else if(getClass() != object.getClass()) {
            return false;
        } else {
            HashableArray obj = (HashableArray) object;
            ArrayList<Integer> otherArray = obj.getArray();

            if(array.size() != otherArray.size()) {
                return false;
            }

            for(int i = 0; i < array.size(); i++) {
                if(!array.get(i).equals(otherArray.get(i))) {
                    return false;
                }
            }
        }

        return true;
    }
}
